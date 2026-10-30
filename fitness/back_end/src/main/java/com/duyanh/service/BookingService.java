package com.duyanh.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.audit.AuditActions;
import com.duyanh.audit.Auditable;
import com.duyanh.config.BookingProperties;
import com.duyanh.dto.booking.BookingResponse;
import com.duyanh.dto.booking.ClassBookingRequest;
import com.duyanh.dto.booking.PtBookingRequest;
import com.duyanh.dto.common.PageResponse;
import com.duyanh.dto.trainer.SlotResponse;
import com.duyanh.entity.Booking;
import com.duyanh.entity.ClassSession;
import com.duyanh.entity.Subscription;
import com.duyanh.entity.TrainerProfile;
import com.duyanh.entity.enums.BookingStatus;
import com.duyanh.entity.enums.BookingType;
import com.duyanh.entity.enums.ClassSessionStatus;
import com.duyanh.event.BookingChangedEvent;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.BookingMapper;
import com.duyanh.repository.BookingRepository;
import com.duyanh.repository.ClassSessionRepository;
import com.duyanh.repository.SubscriptionRepository;
import com.duyanh.repository.UserRepository;
import com.duyanh.security.UserPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Đặt lịch PT 1-1 và đặt chỗ lớp học.
 *
 * Chống đặt trùng có 2 lớp: kiểm tra trước trong code để báo lỗi rõ ràng, và unique key /
 * update nguyên tử trong DB để chặn khi 2 request đến cùng lúc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;
	private static final int MAX_PAGE_SIZE = 100;

	private final BookingRepository bookingRepository;
	private final ClassSessionRepository sessionRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final UserRepository userRepository;
	private final TrainerService trainerService;
	private final BookingProperties props;
	private final ApplicationEventPublisher eventPublisher;
	private final BookingMapper mapper;

	/**
	 * Đặt PT 1-1: giờ phải là 1 slot còn trống, dùng 1 buổi PT trong gói đang hiệu lực vào ngày đó.
	 */
	@Auditable(action = AuditActions.BOOKING_CREATE, entityType = "BOOKING", entityId = "#result.id()",
			details = "{'type': 'PT', 'trainerId': #req.trainerId(), 'startAt': #req.startAt()}")
	@Transactional
	public BookingResponse bookPt(Long memberId, PtBookingRequest req, String idempotencyKey) {
		String key = normalizeKey(idempotencyKey);
		var replay = findReplay(memberId, key, BookingType.PT,
				b -> b.getTrainer().getId().equals(req.trainerId()) && b.getStartAt().equals(req.startAt()));
		if (replay != null) {
			return replay;
		}

		TrainerProfile trainer = trainerService.requireActive(req.trainerId());
		LocalDateTime start = req.startAt();
		LocalDateTime end = start.plusMinutes(props.ptSlotMinutes());
		boolean isFreeSlot = trainerService.computeAvailableSlots(trainer.getId(), start.toLocalDate()).stream()
				.map(SlotResponse::startAt)
				.anyMatch(start::equals);
		if (!isFreeSlot) {
			throw slotNotAvailable();
		}

		Subscription sub = requireUsableSubscription(memberId, start);
		if (sub.getPtSessionsRemaining() <= 0) {
			throw ApiException.badRequest("NO_PT_SESSIONS", "Gói tập của bạn đã hết buổi PT");
		}
		requireNoTimeConflict(memberId, start, end);

		sub.setPtSessionsRemaining(sub.getPtSessionsRemaining() - 1);
		Booking booking = newBooking(memberId, sub, BookingType.PT, start, end, req.note(), key);
		booking.setTrainer(trainer);
		try {
			bookingRepository.saveAndFlush(booking);
		} catch (DataIntegrityViolationException e) {
			// Request khác vừa đặt cùng slot (unique key uk_bookings_trainer_slot)
			throw slotNotAvailable();
		}
		log.info("Member id={} booked PT trainer id={} at {}", memberId, trainer.getId(), start);
		return publish(mapper.toResponse(booking), false);
	}

	/**
	 * Đặt chỗ 1 buổi lớp: giữ chỗ bằng update nguyên tử nên không bao giờ vượt sức chứa.
	 */
	@Auditable(action = AuditActions.BOOKING_CREATE, entityType = "BOOKING", entityId = "#result.id()",
			details = "{'type': 'CLASS', 'classSessionId': #req.classSessionId()}")
	@Transactional
	public BookingResponse bookClass(Long memberId, ClassBookingRequest req, String idempotencyKey) {
		String key = normalizeKey(idempotencyKey);
		var replay = findReplay(memberId, key, BookingType.CLASS,
				b -> b.getClassSession().getId().equals(req.classSessionId()));
		if (replay != null) {
			return replay;
		}

		ClassSession session = sessionRepository.findWithDetailsById(req.classSessionId())
				.orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "Không tìm thấy buổi học"));
		if (session.getStatus() != ClassSessionStatus.SCHEDULED || !session.getStartAt().isAfter(LocalDateTime.now())) {
			throw ApiException.badRequest("SESSION_NOT_AVAILABLE", "Buổi học đã diễn ra hoặc đã bị hủy");
		}
		Subscription sub = requireUsableSubscription(memberId, session.getStartAt());
		if (bookingRepository.existsActiveForSession(memberId, session.getId())) {
			throw ApiException.conflict("ALREADY_BOOKED", "Bạn đã đặt buổi học này rồi");
		}
		requireNoTimeConflict(memberId, session.getStartAt(), session.getEndAt());

		if (sessionRepository.tryReserveSeat(session.getId()) == 0) {
			throw ApiException.conflict("CLASS_FULL", "Buổi học đã hết chỗ");
		}
		Booking booking = newBooking(memberId, sub, BookingType.CLASS, session.getStartAt(), session.getEndAt(),
				req.note(), key);
		booking.setClassSession(session);
		try {
			bookingRepository.saveAndFlush(booking);
		} catch (DataIntegrityViolationException e) {
			// Cùng hội viên gửi 2 request đặt 1 buổi (unique key uk_bookings_member_session);
			// lỗi làm rollback cả lần giữ chỗ ở trên
			throw ApiException.conflict("ALREADY_BOOKED", "Bạn đã đặt buổi học này rồi");
		}
		log.info("Member id={} booked class session id={}", memberId, session.getId());
		return publish(mapper.toResponse(bookingRepository.findByIdAndMemberId(booking.getId(), memberId).orElseThrow()),
				false);
	}

	/**
	 * Hội viên hủy lịch, phải trước giờ tập ít nhất cancel-before-hours.
	 * PT: hoàn lại 1 buổi PT. Lớp: trả lại chỗ.
	 */
	@Auditable(action = AuditActions.BOOKING_CANCEL, entityType = "BOOKING", entityId = "#bookingId",
			details = "{'reason': #reason}")
	@Transactional
	public BookingResponse cancel(Long memberId, Long bookingId, String reason) {
		Booking booking = bookingRepository.findByIdAndMemberId(bookingId, memberId)
				.orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "Không tìm thấy lịch đặt"));
		if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
			throw ApiException.conflict("BOOKING_NOT_CANCELLABLE", "Lịch này không thể hủy");
		}
		LocalDateTime deadline = booking.getStartAt().minusHours(props.cancelBeforeHours());
		if (LocalDateTime.now().isAfter(deadline)) {
			throw ApiException.badRequest("CANCEL_TOO_LATE",
					"Chỉ được hủy trước giờ tập ít nhất %d giờ".formatted(props.cancelBeforeHours()));
		}

		booking.setStatus(BookingStatus.CANCELLED);
		booking.setCancelledAt(LocalDateTime.now());
		booking.setCancelReason(reason == null || reason.isBlank() ? null : reason.trim());
		if (booking.getType() == BookingType.PT) {
			Subscription sub = booking.getSubscription();
			sub.setPtSessionsRemaining(sub.getPtSessionsRemaining() + 1);
		}
		// Map trước: releaseSeat xóa cache của Hibernate, sau đó không đọc được quan hệ lazy nữa
		BookingResponse response = mapper.toResponse(booking);
		if (booking.getType() == BookingType.CLASS) {
			sessionRepository.releaseSeat(booking.getClassSession().getId());
		}
		log.info("Member id={} cancelled booking id={}", memberId, bookingId);
		return publish(response, true);
	}

	/**
	 * Đánh dấu buổi tập đã diễn ra: COMPLETED (có đến tập) hoặc NO_SHOW (vắng mặt).
	 * HLV chỉ đánh dấu buổi của mình (PT với mình hoặc lớp mình dạy); STAFF / ADMIN đánh dấu mọi buổi.
	 * Buổi PT vắng mặt không được hoàn lại buổi PT.
	 */
	@Auditable(action = AuditActions.BOOKING_ATTENDANCE, entityType = "BOOKING", entityId = "#bookingId",
			details = "{'status': #status}")
	@Transactional
	public BookingResponse markAttendance(Long bookingId, BookingStatus status, UserPrincipal actor) {
		if (status != BookingStatus.COMPLETED && status != BookingStatus.NO_SHOW) {
			throw ApiException.badRequest("INVALID_STATUS", "Chỉ được đánh dấu COMPLETED hoặc NO_SHOW");
		}
		Booking booking = bookingRepository.findWithDetailsById(bookingId)
				.orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "Không tìm thấy lịch đặt"));

		boolean isStaff = actor.getRoles().contains("STAFF") || actor.getRoles().contains("ADMIN");
		Long trainerId = booking.getType() == BookingType.PT
				? booking.getTrainer().getId()
				: booking.getClassSession().getTrainer().getId();
		if (!isStaff && !trainerId.equals(actor.getId())) {
			throw ApiException.notFound("BOOKING_NOT_FOUND", "Không tìm thấy lịch đặt");
		}
		if (booking.getStatus() != BookingStatus.CONFIRMED) {
			throw ApiException.conflict("BOOKING_NOT_MARKABLE", "Chỉ đánh dấu được lịch đang CONFIRMED");
		}
		if (booking.getStartAt().isAfter(LocalDateTime.now())) {
			throw ApiException.badRequest("SESSION_NOT_STARTED", "Buổi tập chưa bắt đầu");
		}
		booking.setStatus(status);
		return mapper.toResponse(booking);
	}

	@Transactional(readOnly = true)
	public PageResponse<BookingResponse> listMine(Long memberId, int page, int size) {
		var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
		return PageResponse.of(bookingRepository.findByMemberIdOrderByStartAtDesc(memberId, pageable),
				mapper::toResponse);
	}

	/**
	 * Lịch PT được đặt với HLV trong khoảng [from, to).
	 */
	@Transactional(readOnly = true)
	public List<BookingResponse> listForTrainer(Long trainerId, LocalDateTime from, LocalDateTime to) {
		if (!from.isBefore(to) || from.plusDays(62).isBefore(to)) {
			throw ApiException.badRequest("INVALID_RANGE", "Khoảng thời gian không hợp lệ (tối đa 62 ngày)");
		}
		return bookingRepository.findByTrainerIdAndStartAtBetweenOrderByStartAtAsc(trainerId, from, to.minusNanos(1))
				.stream().map(mapper::toResponse).toList();
	}

	// Phát sự kiện để tạo thông báo cho hội viên, trả lại chính response để viết gọn ở chỗ gọi
	private BookingResponse publish(BookingResponse b, boolean cancelled) {
		String target = b.type() == BookingType.PT
				? "buổi PT với HLV " + b.trainerName()
				: "lớp " + b.className();
		eventPublisher.publishEvent(new BookingChangedEvent(b.id(), b.memberId(), target, b.startAt(), cancelled, null));
		return b;
	}

	private Subscription requireUsableSubscription(Long memberId, LocalDateTime at) {
		return subscriptionRepository.findUsable(memberId, at.toLocalDate())
				.orElseThrow(() -> ApiException.badRequest("NO_ACTIVE_SUBSCRIPTION",
						"Bạn không có gói tập còn hiệu lực vào ngày %s".formatted(at.toLocalDate())));
	}

	private void requireNoTimeConflict(Long memberId, LocalDateTime start, LocalDateTime end) {
		if (bookingRepository.existsActiveOverlap(memberId, start, end)) {
			throw ApiException.conflict("MEMBER_TIME_CONFLICT", "Bạn đã có lịch khác trùng giờ này");
		}
	}

	private Booking newBooking(Long memberId, Subscription sub, BookingType type, LocalDateTime start,
			LocalDateTime end, String note, String key) {
		Booking b = new Booking();
		b.setMember(userRepository.getReferenceById(memberId));
		b.setSubscription(sub);
		b.setType(type);
		b.setStartAt(start);
		b.setEndAt(end);
		b.setStatus(BookingStatus.CONFIRMED);
		b.setNote(note == null || note.isBlank() ? null : note.trim());
		b.setIdempotencyKey(key);
		return b;
	}

	/**
	 * Cùng Idempotency-Key: trả lại lịch cũ nếu cùng nội dung, báo lỗi nếu key bị dùng cho yêu cầu khác.
	 */
	private BookingResponse findReplay(Long memberId, String key, BookingType type,
			Predicate<Booking> sameTarget) {
		if (key == null) {
			return null;
		}
		return bookingRepository.findByMemberIdAndIdempotencyKey(memberId, key)
				.map(b -> {
					if (b.getType() != type || !sameTarget.test(b)) {
						throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
								"Idempotency-Key đã được dùng cho một yêu cầu khác");
					}
					return mapper.toResponse(b);
				})
				.orElse(null);
	}

	private static String normalizeKey(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw ApiException.badRequest("IDEMPOTENCY_KEY_INVALID", "Idempotency-Key tối đa 64 ký tự");
		}
		return key.trim();
	}

	private static ApiException slotNotAvailable() {
		return ApiException.conflict("SLOT_NOT_AVAILABLE", "Khung giờ này không còn trống hoặc không hợp lệ");
	}
}
