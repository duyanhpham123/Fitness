package com.duyanh.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.checkin.CheckInResponse;
import com.duyanh.dto.checkin.ManualCheckInRequest;
import com.duyanh.dto.checkin.QrCodeResponse;
import com.duyanh.dto.checkin.ScanQrRequest;
import com.duyanh.dto.common.PageResponse;
import com.duyanh.security.UserPrincipal;
import com.duyanh.service.CheckInService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Check-in")
@RestController
@RequestMapping("/api/check-ins")
@RequiredArgsConstructor
public class CheckInController {

	private final CheckInService checkInService;

	@Operation(summary = "Hội viên lấy mã QR check-in (hiệu lực 60 giây, dùng 1 lần)")
	@PreAuthorize("hasRole('MEMBER')")
	@GetMapping("/me/qr")
	public QrCodeResponse myQr(@AuthenticationPrincipal UserPrincipal principal) {
		return checkInService.createQr(principal.getId());
	}

	@Operation(summary = "Lễ tân quét QR của hội viên để check-in")
	@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CheckInResponse scan(@Valid @RequestBody ScanQrRequest req, @AuthenticationPrincipal UserPrincipal staff) {
		return checkInService.checkInByQr(req.qrToken(), staff.getId());
	}

	@Operation(summary = "Lễ tân check-in thủ công (hội viên quên điện thoại)")
	@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
	@PostMapping("/manual")
	@ResponseStatus(HttpStatus.CREATED)
	public CheckInResponse manual(@Valid @RequestBody ManualCheckInRequest req,
			@AuthenticationPrincipal UserPrincipal staff) {
		return checkInService.checkInManual(req.memberId(), staff.getId());
	}

	@Operation(summary = "Check-out khi hội viên ra về")
	@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
	@PostMapping("/{id}/check-out")
	public CheckInResponse checkOut(@PathVariable Long id) {
		return checkInService.checkOut(id);
	}

	@Operation(summary = "Lượt check-in hôm nay; openOnly=true chỉ người đang ở phòng tập (STAFF, ADMIN)")
	@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
	@GetMapping("/today")
	public List<CheckInResponse> today(@RequestParam(defaultValue = "false") boolean openOnly) {
		return checkInService.listToday(openOnly);
	}

	@Operation(summary = "Lịch sử ra vào của tôi")
	@PreAuthorize("hasRole('MEMBER')")
	@GetMapping("/me")
	public PageResponse<CheckInResponse> listMine(@AuthenticationPrincipal UserPrincipal principal,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return checkInService.listMine(principal.getId(), page, size);
	}
}
