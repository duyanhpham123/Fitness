package com.duyanh.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.audit.AuditActions;
import com.duyanh.audit.Auditable;
import com.duyanh.dto.membership.PurchaseRequest;
import com.duyanh.dto.membership.PurchaseResponse;
import com.duyanh.dto.membership.SubscriptionResponse;
import com.duyanh.entity.MembershipPackage;
import com.duyanh.entity.Payment;
import com.duyanh.entity.Subscription;
import com.duyanh.entity.User;
import com.duyanh.entity.enums.PaymentMethod;
import com.duyanh.entity.enums.SubscriptionStatus;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.MembershipMapper;
import com.duyanh.repository.BookingRepository;
import com.duyanh.repository.MembershipPackageRepository;
import com.duyanh.repository.PaymentRepository;
import com.duyanh.repository.SubscriptionRepository;
import com.duyanh.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mua / gia hạn gói, kích hoạt sau khi thanh toán, bảo lưu.
 *
 * Vòng đời: PENDING (chờ thanh toán) -> ACTIVE -> FROZEN (bảo lưu) -> ACTIVE -> EXPIRED.
 * Gia hạn khi đang còn gói: gói mới ACTIVE nhưng start_date nằm sau end_date của gói hiện tại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;
	// MoMo / ZaloPay chưa tích hợp: không cho tạo đơn để tránh đơn treo không thanh toán được
	private static final Set<PaymentMethod> SUPPORTED_METHODS =
			EnumSet.of(PaymentMethod.VNPAY, PaymentMethod.CASH, PaymentMethod.BANK_TRANSFER);
	private static final DateTimeFormatter CODE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final SecureRandom RANDOM = new SecureRandom();

	private final SubscriptionRepository subscriptionRepository;
	private final BookingRepository bookingRepository;
	private final MembershipPackageRepository packageRepository;
	private final PaymentRepository paymentRepository;
	private final UserRepository userRepository;
	private final MembershipMapper mapper;

	/**
	 * Tạo Subscription PENDING + Payment PENDING. Gói được kích hoạt khi thanh toán thành công
	 * (nhân viên xác nhận tiền mặt, hoặc IPN của cổng thanh toán).
	 *
	 * Gửi lại cùng Idempotency-Key -> trả lại đơn cũ, không tạo đơn mới.
	 */
	@Auditable(action = AuditActions.SUBSCRIPTION_PURCHASE, entityType = "SUBSCRIPTION",
			entityId = "#result.subscription().id()",
			details = "{'packageId': #req.packageId(), 'method': #req.method()}")
	@Transactional
	public PurchaseResponse purchase(Long userId, PurchaseRequest req, String idempotencyKey) {
		String key = normalizeIdempotencyKey(idempotencyKey);
		if (key != null) {
			var existing = paymentRepository.findByUserIdAndIdempotencyKey(userId, key);
			if (existing.isPresent()) {
				Payment payment = existing.get();
				Subscription sub = payment.getSubscription();
				if (sub == null || !sub.getMembershipPackage().getId().equals(req.packageId())) {
					throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
							"Idempotency-Key đã được dùng cho một yêu cầu khác");
				}
				return new PurchaseResponse(mapper.toResponse(sub), mapper.toResponse(payment));
			}
		}

		if (!SUPPORTED_METHODS.contains(req.method())) {
			throw ApiException.badRequest("PAYMENT_METHOD_NOT_SUPPORTED",
					"Hiện chỉ hỗ trợ thanh toán VNPay, tiền mặt hoặc chuyển khoản");
		}
		MembershipPackage pkg = packageRepository.findById(req.packageId())
				.filter(MembershipPackage::isActive)
				.orElseThrow(() -> ApiException.badRequest("PACKAGE_NOT_AVAILABLE", "Gói tập không tồn tại hoặc đã ngừng bán"));
		User user = userRepository.getReferenceById(userId);

		Subscription sub = new Subscription();
		sub.setUser(user);
		sub.setMembershipPackage(pkg);
		sub.setPricePaid(pkg.getPrice());
		subscriptionRepository.save(sub);

		// Gói miễn phí: kích hoạt luôn, không tạo payment (DB yêu cầu amount > 0)
		if (pkg.getPrice().compareTo(BigDecimal.ZERO) == 0) {
			activate(sub);
			return new PurchaseResponse(mapper.toResponse(sub), null);
		}

		Payment payment = new Payment();
		payment.setCode(generatePaymentCode());
		payment.setUser(user);
		payment.setSubscription(sub);
		payment.setAmount(pkg.getPrice());
		payment.setMethod(req.method());
		payment.setIdempotencyKey(key);
		paymentRepository.save(payment);

		log.info("User id={} purchased package {} -> subscription id={}, payment {}",
				userId, pkg.getCode(), sub.getId(), payment.getCode());
		return new PurchaseResponse(mapper.toResponse(sub), mapper.toResponse(payment));
	}

	/**
	 * Kích hoạt gói sau khi thanh toán thành công. Gọi trong cùng transaction với việc
	 * cập nhật Payment = SUCCESS.
	 */
	@Transactional
	public void activate(Subscription sub) {
		if (sub.getStatus() != SubscriptionStatus.PENDING) {
			throw ApiException.conflict("SUBSCRIPTION_NOT_PENDING", "Gói tập không ở trạng thái chờ thanh toán");
		}
		LocalDate today = LocalDate.now();
		LocalDate start = subscriptionRepository.findLatestEndDate(sub.getUser().getId(), sub.getId())
				.map(latestEnd -> latestEnd.plusDays(1))
				.filter(nextDay -> nextDay.isAfter(today))
				.orElse(today);
		MembershipPackage pkg = sub.getMembershipPackage();

		sub.setStartDate(start);
		sub.setEndDate(start.plusDays(pkg.getDurationDays() - 1L));
		sub.setPtSessionsRemaining(pkg.getPtSessions());
		sub.setStatus(SubscriptionStatus.ACTIVE);
		log.info("Activated subscription id={} from {} to {}", sub.getId(), sub.getStartDate(), sub.getEndDate());
	}

	@Transactional(readOnly = true)
	public List<SubscriptionResponse> listMine(Long userId) {
		return subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(mapper::toResponse)
				.toList();
	}

	/**
	 * Bảo lưu từ hôm nay trong N ngày: gói lùi ngày kết thúc thêm N ngày,
	 * các gói đã mua nối tiếp phía sau cũng lùi theo.
	 */
	@Auditable(action = AuditActions.SUBSCRIPTION_FREEZE, entityType = "SUBSCRIPTION", entityId = "#subscriptionId",
			details = "{'days': #days}")
	@Transactional
	public SubscriptionResponse freeze(Long userId, Long subscriptionId, int days) {
		Subscription sub = findOwned(userId, subscriptionId);
		LocalDate today = LocalDate.now();

		if (sub.getStatus() != SubscriptionStatus.ACTIVE || !sub.isUsableOn(today)) {
			throw ApiException.conflict("SUBSCRIPTION_NOT_FREEZABLE", "Chỉ bảo lưu được gói đang trong thời gian sử dụng");
		}
		int remaining = sub.getMembershipPackage().getMaxFreezeDays() - sub.getFrozenDaysUsed();
		if (days > remaining) {
			throw ApiException.badRequest("FREEZE_LIMIT_EXCEEDED",
					"Gói chỉ còn được bảo lưu tối đa %d ngày".formatted(remaining));
		}

		LocalDate frozenUntil = today.plusDays(days - 1L);
		// Chỉ xét lịch từ bây giờ: lịch đã tập sáng nay không cản việc bảo lưu
		if (bookingRepository.existsActiveOverlap(userId, LocalDateTime.now(), frozenUntil.plusDays(1).atStartOfDay())) {
			throw ApiException.conflict("HAS_BOOKINGS_IN_FREEZE_PERIOD",
					"Bạn còn lịch tập trong thời gian bảo lưu, hãy hủy lịch trước");
		}

		shiftQueued(userId, sub.getEndDate(), days);
		sub.setStatus(SubscriptionStatus.FROZEN);
		sub.setFrozenFrom(today);
		sub.setFrozenUntil(frozenUntil);
		sub.setFrozenDaysUsed(sub.getFrozenDaysUsed() + days);
		sub.setEndDate(sub.getEndDate().plusDays(days));
		return mapper.toResponse(sub);
	}

	/**
	 * Hủy bảo lưu sớm: hoàn lại số ngày bảo lưu chưa dùng (tính cả hôm nay).
	 */
	@Auditable(action = AuditActions.SUBSCRIPTION_UNFREEZE, entityType = "SUBSCRIPTION", entityId = "#subscriptionId")
	@Transactional
	public SubscriptionResponse unfreeze(Long userId, Long subscriptionId) {
		Subscription sub = findOwned(userId, subscriptionId);
		if (sub.getStatus() != SubscriptionStatus.FROZEN) {
			throw ApiException.conflict("SUBSCRIPTION_NOT_FROZEN", "Gói tập không ở trạng thái bảo lưu");
		}
		LocalDate today = LocalDate.now();
		int unused = (int) Math.max(0, ChronoUnit.DAYS.between(today, sub.getFrozenUntil()) + 1);

		LocalDate oldEnd = sub.getEndDate();
		sub.setStatus(SubscriptionStatus.ACTIVE);
		sub.setFrozenDaysUsed(sub.getFrozenDaysUsed() - unused);
		sub.setEndDate(oldEnd.minusDays(unused));
		if (today.isAfter(sub.getFrozenFrom())) {
			sub.setFrozenUntil(today.minusDays(1));
		} else {
			// Hủy ngay trong ngày bắt đầu bảo lưu: coi như chưa bảo lưu
			sub.setFrozenFrom(null);
			sub.setFrozenUntil(null);
		}
		shiftQueued(userId, oldEnd, -unused);
		return mapper.toResponse(sub);
	}

	private void shiftQueued(Long userId, LocalDate after, int days) {
		if (days == 0) {
			return;
		}
		List<Subscription> queued = subscriptionRepository.findQueued(userId, after);
		for (Subscription q : queued) {
			q.setStartDate(q.getStartDate().plusDays(days));
			q.setEndDate(q.getEndDate().plusDays(days));
		}
	}

	private Subscription findOwned(Long userId, Long subscriptionId) {
		return subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
				.orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "Không tìm thấy gói tập"));
	}

	private static String normalizeIdempotencyKey(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw ApiException.badRequest("IDEMPOTENCY_KEY_INVALID", "Idempotency-Key tối đa 64 ký tự");
		}
		return key.trim();
	}

	// Ví dụ: PAY20261030091500123456 (23 ký tự, cột code tối đa 40)
	private static String generatePaymentCode() {
		return "PAY" + LocalDateTime.now().format(CODE_TIME) + "%06d".formatted(RANDOM.nextInt(1_000_000));
	}
}
