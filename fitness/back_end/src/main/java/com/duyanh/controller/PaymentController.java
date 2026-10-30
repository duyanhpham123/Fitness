package com.duyanh.controller;

import java.net.URI;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.duyanh.config.VnpayProperties;
import com.duyanh.dto.common.PageResponse;
import com.duyanh.dto.membership.PurchaseResponse;
import com.duyanh.dto.payment.PaymentResponse;
import com.duyanh.dto.payment.PaymentUrlResponse;
import com.duyanh.dto.payment.VnpayIpnResponse;
import com.duyanh.entity.enums.PaymentStatus;
import com.duyanh.security.UserPrincipal;
import com.duyanh.service.PaymentService;
import com.duyanh.service.PaymentService.VnpayResult;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Payment")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;
	private final VnpayProperties vnpayProperties;

	@Operation(summary = "Lịch sử giao dịch của tôi")
	@GetMapping("/me")
	public PageResponse<PaymentResponse> listMine(@AuthenticationPrincipal UserPrincipal principal,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return paymentService.listMine(principal.getId(), page, size);
	}

	@Operation(summary = "Danh sách giao dịch, lọc theo trạng thái (STAFF, ADMIN)")
	@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
	@GetMapping
	public PageResponse<PaymentResponse> search(@RequestParam(required = false) PaymentStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return paymentService.search(status, page, size);
	}

	@Operation(summary = "Chi tiết giao dịch (chủ giao dịch, STAFF, ADMIN)")
	@GetMapping("/{id}")
	public PaymentResponse get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
		return paymentService.get(id, principal);
	}

	@Operation(summary = "Tạo link thanh toán VNPay cho đơn đang chờ; frontend chuyển trình duyệt tới paymentUrl")
	@PreAuthorize("hasRole('MEMBER')")
	@PostMapping("/{id}/vnpay-url")
	public PaymentUrlResponse createVnpayUrl(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal,
			HttpServletRequest http) {
		return paymentService.createVnpayUrl(id, principal.getId(), http.getRemoteAddr());
	}

	@Operation(summary = "Xác nhận đã nhận tiền mặt / chuyển khoản tại quầy, kích hoạt gói (STAFF, ADMIN)")
	@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
	@PostMapping("/{id}/confirm")
	public PurchaseResponse confirm(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
		return paymentService.confirmManual(id, principal.getId());
	}

	/**
	 * VNPay gọi server-to-server sau khi thanh toán. Luôn trả HTTP 200 với RspCode,
	 * kể cả khi lỗi (99), vì VNPay chỉ đọc RspCode để quyết định có gọi lại hay không.
	 */
	@Hidden
	@GetMapping("/vnpay/ipn")
	public VnpayIpnResponse vnpayIpn(@RequestParam Map<String, String> params) {
		try {
			VnpayResult result = paymentService.handleVnpayCallback(params);
			return new VnpayIpnResponse(result.rspCode(), result.message());
		} catch (Exception e) {
			log.error("VNPay IPN failed, txnRef={}", params.get("vnp_TxnRef"), e);
			return new VnpayIpnResponse("99", "Unknown error");
		}
	}

	/**
	 * VNPay chuyển trình duyệt về đây. Xử lý giống IPN (để chạy được ở máy local, nơi VNPay
	 * không gọi IPN tới localhost), rồi redirect sang trang kết quả của frontend.
	 */
	@Hidden
	@GetMapping("/vnpay/return")
	public ResponseEntity<Void> vnpayReturn(@RequestParam Map<String, String> params) {
		String result;
		String paymentCode = params.get("vnp_TxnRef");
		try {
			VnpayResult r = paymentService.handleVnpayCallback(params);
			result = switch (r.rspCode()) {
				case "97" -> "INVALID";
				case "01" -> "NOT_FOUND";
				default -> r.paymentStatus() == null ? "ERROR" : r.paymentStatus().name();
			};
		} catch (Exception e) {
			log.error("VNPay return failed, txnRef={}", paymentCode, e);
			result = "ERROR";
		}
		URI target = UriComponentsBuilder.fromUriString(vnpayProperties.feResultUrl())
				.queryParam("paymentCode", paymentCode)
				.queryParam("result", result)
				.encode()
				.build()
				.toUri();
		return ResponseEntity.status(302).location(target).build();
	}
}
