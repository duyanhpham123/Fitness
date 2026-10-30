package com.duyanh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.duyanh.audit.AuditActions;
import com.duyanh.audit.Auditable;
import com.duyanh.dto.common.PageResponse;
import com.duyanh.dto.notification.BroadcastRequest;
import com.duyanh.dto.notification.CountResponse;
import com.duyanh.dto.notification.NotificationResponse;
import com.duyanh.entity.Booking;
import com.duyanh.entity.Notification;
import com.duyanh.entity.Subscription;
import com.duyanh.entity.User;
import com.duyanh.entity.enums.BookingStatus;
import com.duyanh.entity.enums.BookingType;
import com.duyanh.entity.enums.NotificationType;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.NotificationMapper;
import com.duyanh.repository.BookingRepository;
import com.duyanh.repository.NotificationRepository;
import com.duyanh.repository.SubscriptionRepository;
import com.duyanh.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final String REF_SUBSCRIPTION_EXPIRING = "SUBSCRIPTION_EXPIRING";
	private static final String REF_BOOKING_REMINDER = "BOOKING_REMINDER";

	private final NotificationRepository notificationRepository;
	private final BookingRepository bookingRepository;
	private final UserRepository userRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final EmailService emailService;
	private final NotificationMapper mapper;

	/**
	 * Lưu thông báo trong app (chung transaction với thao tác gọi tới).
	 *
	 * @param alsoEmail gửi thêm email, chỉ gửi SAU KHI transaction commit thành công
	 */
	@Transactional
	public void notify(Long userId, NotificationType type, String title, String content, String referenceType,
			Long referenceId, boolean alsoEmail) {
		User user = userRepository.findById(userId).orElseThrow();
		Notification n = new Notification();
		n.setUser(user);
		n.setType(type);
		n.setTitle(title);
		n.setContent(content);
		n.setReferenceType(referenceType);
		n.setReferenceId(referenceId);
		notificationRepository.save(n);

		if (alsoEmail) {
			String email = user.getEmail();
			afterCommit(() -> emailService.send(email, "[Fitness] " + title, content));
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> listMine(Long userId, int page, int size) {
		var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
		return PageResponse.of(notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable),
				mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public CountResponse unreadCount(Long userId) {
		return new CountResponse(notificationRepository.countByUserIdAndReadFalse(userId));
	}

	@Transactional
	public void markRead(Long userId, Long notificationId) {
		if (!notificationRepository.existsByIdAndUserId(notificationId, userId)) {
			throw ApiException.notFound("NOTIFICATION_NOT_FOUND", "Không tìm thấy thông báo");
		}
		notificationRepository.markRead(notificationId, userId, LocalDateTime.now());
	}

	@Transactional
	public CountResponse markAllRead(Long userId) {
		return new CountResponse(notificationRepository.markAllRead(userId, LocalDateTime.now()));
	}

	/**
	 * ADMIN gửi thông báo chung: tạo 1 dòng cho mỗi tài khoản đang hoạt động (theo role nếu có).
	 */
	@Auditable(action = AuditActions.NOTIFICATION_BROADCAST, entityType = "NOTIFICATION",
			details = "{'title': #req.title(), 'role': #req.role(), 'recipients': #result.count()}")
	@Transactional
	public CountResponse broadcast(BroadcastRequest req) {
		List<Long> userIds = userRepository.findActiveIds(req.role());
		List<Notification> rows = userIds.stream().map(id -> {
			Notification n = new Notification();
			n.setUser(userRepository.getReferenceById(id));
			n.setType(req.type());
			n.setTitle(req.title().trim());
			n.setContent(req.content().trim());
			return n;
		}).toList();
		notificationRepository.saveAll(rows);
		log.info("Broadcast '{}' to {} users", req.title(), rows.size());
		return new CountResponse(rows.size());
	}

	/**
	 * Nhắc hội viên có gói hết hạn đúng ngày endDate. Chạy lại trong ngày không gửi trùng.
	 *
	 * @return số thông báo đã tạo
	 */
	@Transactional
	public int remindExpiring(LocalDate endDate) {
		int created = 0;
		for (Subscription sub : subscriptionRepository.findActiveEndingOn(endDate)) {
			Long userId = sub.getUser().getId();
			if (notificationRepository.existsByUserIdAndReferenceTypeAndReferenceId(userId, REF_SUBSCRIPTION_EXPIRING,
					sub.getId())) {
				continue;
			}
			notify(userId, NotificationType.MEMBERSHIP, "Gói tập sắp hết hạn",
					"%s của bạn hết hạn ngày %s. Gia hạn ngay để không bị gián đoạn lịch tập.".formatted(
							sub.getMembershipPackage().getName(),
							endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
					REF_SUBSCRIPTION_EXPIRING, sub.getId(), true);
			created++;
		}
		return created;
	}

	/**
	 * Nhắc hội viên các lịch tập (PT / lớp) đang CONFIRMED trong ngày cho trước. Chạy lại không gửi trùng.
	 *
	 * @return số thông báo đã tạo
	 */
	@Transactional
	public int remindBookings(LocalDate day) {
		LocalDateTime start = day.atStartOfDay();
		int created = 0;
		for (Booking b : bookingRepository.findByStartAtBetweenAndStatusInOrderByStartAtAsc(start,
				start.plusDays(1).minusNanos(1), List.of(BookingStatus.CONFIRMED))) {
			Long memberId = b.getMember().getId();
			if (notificationRepository.existsByUserIdAndReferenceTypeAndReferenceId(memberId, REF_BOOKING_REMINDER,
					b.getId())) {
				continue;
			}
			String target = b.getType() == BookingType.PT
					? "buổi PT với HLV " + b.getTrainer().getUser().getFullName()
					: "lớp " + b.getClassSession().getFitnessClass().getName();
			notify(memberId, NotificationType.BOOKING, "Nhắc lịch tập",
					"Bạn có %s lúc %s. Nếu không đến được, hãy hủy trước ít nhất 2 giờ.".formatted(target,
							b.getStartAt().format(DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy"))),
					REF_BOOKING_REMINDER, b.getId(), true);
			created++;
		}
		return created;
	}

	// Không có transaction (vd. gọi từ test hoặc job không transaction) thì chạy luôn
	private static void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		} else {
			action.run();
		}
	}
}
