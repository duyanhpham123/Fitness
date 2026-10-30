package com.duyanh.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.membership.FreezeRequest;
import com.duyanh.dto.membership.PurchaseRequest;
import com.duyanh.dto.membership.PurchaseResponse;
import com.duyanh.dto.membership.SubscriptionResponse;
import com.duyanh.security.UserPrincipal;
import com.duyanh.service.SubscriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Membership - Gói của hội viên")
@RestController
@RequestMapping("/api/subscriptions")
@PreAuthorize("hasRole('MEMBER')")
@RequiredArgsConstructor
public class SubscriptionController {

	private final SubscriptionService subscriptionService;

	@Operation(summary = "Mua / gia hạn gói. Tạo đơn chờ thanh toán; gửi lại cùng Idempotency-Key sẽ trả về đơn cũ")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PurchaseResponse purchase(@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody PurchaseRequest req,
			@Parameter(description = "Chuỗi ngẫu nhiên (vd. UUID) do client tạo cho mỗi lần bấm mua, tối đa 64 ký tự")
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		return subscriptionService.purchase(principal.getId(), req, idempotencyKey);
	}

	@Operation(summary = "Danh sách gói đã mua của tôi")
	@GetMapping("/me")
	public List<SubscriptionResponse> listMine(@AuthenticationPrincipal UserPrincipal principal) {
		return subscriptionService.listMine(principal.getId());
	}

	@Operation(summary = "Bảo lưu gói từ hôm nay trong N ngày")
	@PostMapping("/{id}/freeze")
	public SubscriptionResponse freeze(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
			@Valid @RequestBody FreezeRequest req) {
		return subscriptionService.freeze(principal.getId(), id, req.days());
	}

	@Operation(summary = "Hủy bảo lưu sớm, hoàn lại số ngày bảo lưu chưa dùng")
	@PostMapping("/{id}/unfreeze")
	public SubscriptionResponse unfreeze(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
		return subscriptionService.unfreeze(principal.getId(), id);
	}
}
