package com.duyanh.service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.audit.AuditActions;
import com.duyanh.audit.Auditable;
import com.duyanh.config.VnpayProperties;
import com.duyanh.dto.common.PageResponse;
import com.duyanh.dto.membership.PurchaseResponse;
import com.duyanh.dto.payment.PaymentResponse;
import com.duyanh.dto.payment.PaymentUrlResponse;
import com.duyanh.entity.Payment;
import com.duyanh.entity.Subscription;
import com.duyanh.entity.enums.PaymentMethod;
import com.duyanh.entity.enums.PaymentStatus;
import com.duyanh.entity.enums.SubscriptionStatus;
import com.duyanh.event.PaymentResultEvent;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.MembershipMapper;
import com.duyanh.repository.PaymentRepository;
import com.duyanh.repository.SubscriptionRepository;
import com.duyanh.security.UserPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

	// Chỉ các phương thức thu tại quầy mới được nhân viên xác nhận tay; VNPay xác nhận qua IPN
	private static final Set<PaymentMethod> MANUAL_METHODS = EnumSet.of(PaymentMethod.CASH, PaymentMethod.BANK_TRANSFER);
	private static final Set<PaymentMethod> ONLINE_METHODS = EnumSet.of(PaymentMethod.VNPAY, PaymentMethod.MOMO,
			PaymentMethod.ZALOPAY);
	private static final int MAX_PAGE_SIZE = 100;

	private final PaymentRepository paymentRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final SubscriptionService subscriptionService;
	private final VnpayService vnpayService;
	private final VnpayProperties vnpayProperties;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;
	private final MembershipMapper mapper;

	/**
	 * Nhân viên xác nhận đã nhận tiền -> Payment SUCCESS và kích hoạt gói trong cùng 1 transaction.
	 */
	@Auditable(action = AuditActions.PAYMENT_CONFIRM, entityType = "PAYMENT", entityId = "#paymentId")
	@Transactional
	public PurchaseResponse confirmManual(Long paymentId, Long staffId) {
		Payment payment = paymentRepository.findByIdForUpdate(paymentId)
				.orElseThrow(PaymentService::paymentNotFound);
		if (!MANUAL_METHODS.contains(payment.getMethod())) {
			throw ApiException.badRequest("PAYMENT_METHOD_NOT_MANUAL",
					"Giao dịch %s được xác nhận tự động qua cổng thanh toán".formatted(payment.getMethod()));
		}
		if (payment.getStatus() != PaymentStatus.PENDING) {
			throw ApiException.conflict("PAYMENT_NOT_PENDING", "Giao dịch không ở trạng thái chờ thanh toán");
		}

		payment.setStatus(PaymentStatus.SUCCESS);
		payment.setPaidAt(LocalDateTime.now());
		Subscription sub = payment.getSubscription();
		if (sub != null) {
			subscriptionService.activate(sub);
		}
		log.info("Staff id={} confirmed payment {}", staffId, payment.getCode());
		publishResult(payment, true);
		return new PurchaseResponse(sub == null ? null : mapper.toResponse(sub), mapper.toResponse(payment));
	}

	/**
	 * Tạo link thanh toán VNPay cho đơn đang chờ. Gọi lại được nếu người dùng lỡ đóng trang thanh toán.
	 */
	@Transactional(readOnly = true)
	public PaymentUrlResponse createVnpayUrl(Long paymentId, Long userId, String clientIp) {
		if (!vnpayProperties.isConfigured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VNPAY_NOT_CONFIGURED",
					"Chưa cấu hình VNPay (app.vnpay.tmn-code / app.vnpay.hash-secret)");
		}
		Payment payment = paymentRepository.findById(paymentId)
				.filter(p -> p.getUser().getId().equals(userId))
				.orElseThrow(PaymentService::paymentNotFound);
		if (payment.getMethod() != PaymentMethod.VNPAY) {
			throw ApiException.badRequest("PAYMENT_METHOD_NOT_VNPAY", "Giao dịch không dùng phương thức VNPay");
		}
		if (payment.getStatus() != PaymentStatus.PENDING) {
			throw ApiException.conflict("PAYMENT_NOT_PENDING", "Giao dịch không ở trạng thái chờ thanh toán");
		}
		VnpayService.PaymentUrl url = vnpayService.buildPaymentUrl(payment, clientIp);
		return new PaymentUrlResponse(url.url(), url.expiresAt());
	}

	/**
	 * Xử lý kết quả VNPay gửi về (IPN server-to-server, hoặc return URL qua trình duyệt).
	 * Cả hai đều có chữ ký nên xử lý chung; gọi nhiều lần cũng chỉ ghi nhận 1 lần.
	 *
	 * Mã phản hồi theo tài liệu VNPay: 00 thành công, 01 không tìm thấy đơn, 02 đơn đã xử lý,
	 * 04 sai số tiền, 97 sai chữ ký.
	 */
	@Transactional
	public VnpayResult handleVnpayCallback(Map<String, String> params) {
		if (!vnpayProperties.isConfigured() || !vnpayService.verifySignature(params)) {
			log.warn("VNPay callback with invalid signature, txnRef={}", params.get("vnp_TxnRef"));
			return VnpayResult.of("97", "Invalid signature", null);
		}

		String code = params.get("vnp_TxnRef");
		Payment payment = code == null ? null : paymentRepository.findByCodeForUpdate(code)
				.filter(p -> p.getMethod() == PaymentMethod.VNPAY)
				.orElse(null);
		if (payment == null) {
			return VnpayResult.of("01", "Order not found", null);
		}
		if (!String.valueOf(VnpayService.toVnpAmount(payment.getAmount())).equals(params.get("vnp_Amount"))) {
			log.warn("VNPay amount mismatch for {}: {}", code, params.get("vnp_Amount"));
			return VnpayResult.of("04", "Invalid amount", payment);
		}
		if (payment.getStatus() != PaymentStatus.PENDING) {
			return VnpayResult.of("02", "Order already confirmed", payment);
		}

		boolean success = "00".equals(params.get("vnp_ResponseCode"))
				&& "00".equals(params.get("vnp_TransactionStatus"));
		payment.setGatewayResponseCode(params.get("vnp_ResponseCode"));
		payment.setRawCallback(new HashMap<>(params));
		Subscription sub = payment.getSubscription();

		if (success) {
			// Chỉ lưu mã giao dịch khi thành công: giao dịch lỗi có thể trả vnp_TransactionNo = 0,
			// sẽ đụng unique key (method, gateway_txn_id)
			payment.setGatewayTxnId(params.get("vnp_TransactionNo"));
			payment.setStatus(PaymentStatus.SUCCESS);
			payment.setPaidAt(params.get("vnp_PayDate") != null
					? VnpayService.parsePayDate(params.get("vnp_PayDate"))
					: LocalDateTime.now());
			if (sub != null && sub.getStatus() == SubscriptionStatus.PENDING) {
				subscriptionService.activate(sub);
			} else if (sub != null) {
				log.error("Payment {} paid but subscription id={} is {}, cần kiểm tra / hoàn tiền thủ công",
						code, sub.getId(), sub.getStatus());
			}
			log.info("VNPay payment {} SUCCESS, txn={}", code, payment.getGatewayTxnId());
		} else {
			payment.setStatus(PaymentStatus.FAILED);
			if (sub != null && sub.getStatus() == SubscriptionStatus.PENDING) {
				sub.setStatus(SubscriptionStatus.CANCELLED);
			}
			log.info("VNPay payment {} FAILED, responseCode={}", code, params.get("vnp_ResponseCode"));
		}
		// actor = null: do cổng thanh toán gọi tới, không phải người dùng
		auditService.record(null, success ? AuditActions.PAYMENT_SUCCESS : AuditActions.PAYMENT_FAILED, "PAYMENT",
				payment.getId(), Map.of("gateway", "VNPAY", "responseCode", String.valueOf(params.get("vnp_ResponseCode"))));
		publishResult(payment, success);
		return VnpayResult.of("00", "Confirm Success", payment);
	}

	@Transactional(readOnly = true)
	public PageResponse<PaymentResponse> listMine(Long userId, int page, int size) {
		var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
		return PageResponse.of(paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable), mapper::toResponse);
	}

	/**
	 * Chủ giao dịch, STAFF hoặc ADMIN được xem.
	 */
	@Transactional(readOnly = true)
	public PaymentResponse get(Long paymentId, UserPrincipal viewer) {
		boolean isStaff = viewer.getRoles().contains("STAFF") || viewer.getRoles().contains("ADMIN");
		return paymentRepository.findById(paymentId)
				.filter(p -> isStaff || p.getUser().getId().equals(viewer.getId()))
				.map(mapper::toResponse)
				.orElseThrow(PaymentService::paymentNotFound);
	}

	/**
	 * Hủy các đơn thanh toán online tạo trước cutoff mà vẫn chưa có kết quả (người dùng bỏ dở),
	 * kèm gói PENDING tương ứng.
	 */
	@Transactional
	public int cancelExpiredOnline(LocalDateTime cutoff) {
		int subs = subscriptionRepository.cancelPendingOfExpiredPayments(ONLINE_METHODS, cutoff);
		int payments = paymentRepository.cancelExpired(ONLINE_METHODS, cutoff);
		if (payments > 0) {
			log.info("Cancelled {} expired online payments, {} subscriptions", payments, subs);
		}
		return payments;
	}

	private void publishResult(Payment payment, boolean success) {
		Subscription sub = payment.getSubscription();
		boolean activated = success && sub != null && sub.getStatus() != SubscriptionStatus.PENDING;
		eventPublisher.publishEvent(new PaymentResultEvent(payment.getId(), payment.getUser().getId(),
				payment.getCode(), payment.getAmount(), success,
				sub == null ? "gói tập" : sub.getMembershipPackage().getName(),
				activated ? sub.getStartDate() : null,
				activated ? sub.getEndDate() : null));
	}

	private static ApiException paymentNotFound() {
		return ApiException.notFound("PAYMENT_NOT_FOUND", "Không tìm thấy giao dịch");
	}

	/**
	 * @param payment null khi không tìm thấy đơn hoặc sai chữ ký
	 */
	public record VnpayResult(String rspCode, String message, String paymentCode, PaymentStatus paymentStatus) {

		static VnpayResult of(String rspCode, String message, Payment payment) {
			return new VnpayResult(rspCode, message,
					payment == null ? null : payment.getCode(),
					payment == null ? null : payment.getStatus());
		}
	}
}
