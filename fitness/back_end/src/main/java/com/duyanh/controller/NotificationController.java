package com.duyanh.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.common.PageResponse;
import com.duyanh.dto.notification.BroadcastRequest;
import com.duyanh.dto.notification.CountResponse;
import com.duyanh.dto.notification.NotificationResponse;
import com.duyanh.security.UserPrincipal;
import com.duyanh.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Notification")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@Operation(summary = "Thông báo của tôi, mới nhất trước")
	@GetMapping("/me")
	public PageResponse<NotificationResponse> listMine(@AuthenticationPrincipal UserPrincipal principal,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return notificationService.listMine(principal.getId(), page, size);
	}

	@Operation(summary = "Số thông báo chưa đọc (hiển thị trên icon chuông)")
	@GetMapping("/me/unread-count")
	public CountResponse unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
		return notificationService.unreadCount(principal.getId());
	}

	@Operation(summary = "Đánh dấu 1 thông báo đã đọc")
	@PutMapping("/{id}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markRead(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
		notificationService.markRead(principal.getId(), id);
	}

	@Operation(summary = "Đánh dấu tất cả đã đọc, trả về số thông báo vừa cập nhật")
	@PutMapping("/read-all")
	public CountResponse markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
		return notificationService.markAllRead(principal.getId());
	}

	@Operation(summary = "Gửi thông báo chung (ADMIN), lọc theo role nếu có; trả về số người nhận")
	@PreAuthorize("hasRole('ADMIN')")
	@PostMapping("/broadcast")
	public CountResponse broadcast(@Valid @RequestBody BroadcastRequest req) {
		return notificationService.broadcast(req);
	}
}
