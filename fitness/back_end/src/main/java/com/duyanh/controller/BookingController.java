package com.duyanh.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.booking.AttendanceRequest;
import com.duyanh.dto.booking.BookingResponse;
import com.duyanh.dto.booking.ClassBookingRequest;
import com.duyanh.dto.booking.PtBookingRequest;
import com.duyanh.dto.common.PageResponse;
import com.duyanh.security.UserPrincipal;
import com.duyanh.service.BookingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Booking")
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

	private final BookingService bookingService;

	@Operation(summary = "Đặt PT 1-1 (dùng 1 buổi PT trong gói). startAt lấy từ GET /api/trainers/{id}/slots")
	@PreAuthorize("hasRole('MEMBER')")
	@PostMapping("/pt")
	@ResponseStatus(HttpStatus.CREATED)
	public BookingResponse bookPt(@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody PtBookingRequest req,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		return bookingService.bookPt(principal.getId(), req, idempotencyKey);
	}

	@Operation(summary = "Đặt chỗ 1 buổi lớp học")
	@PreAuthorize("hasRole('MEMBER')")
	@PostMapping("/class")
	@ResponseStatus(HttpStatus.CREATED)
	public BookingResponse bookClass(@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody ClassBookingRequest req,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		return bookingService.bookClass(principal.getId(), req, idempotencyKey);
	}

	@Operation(summary = "Hủy lịch (trước giờ tập ít nhất 2 giờ)")
	@PreAuthorize("hasRole('MEMBER')")
	@DeleteMapping("/{id}")
	public BookingResponse cancel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
			@RequestParam(required = false) String reason) {
		return bookingService.cancel(principal.getId(), id, reason);
	}

	@Operation(summary = "Đánh dấu đã tập (COMPLETED) / vắng mặt (NO_SHOW) sau khi buổi tập bắt đầu "
			+ "(HLV cho buổi của mình; STAFF, ADMIN cho mọi buổi)")
	@PreAuthorize("hasAnyRole('TRAINER', 'STAFF', 'ADMIN')")
	@PutMapping("/{id}/attendance")
	public BookingResponse markAttendance(@PathVariable Long id, @Valid @RequestBody AttendanceRequest req,
			@AuthenticationPrincipal UserPrincipal principal) {
		return bookingService.markAttendance(id, req.status(), principal);
	}

	@Operation(summary = "Lịch sử đặt lịch của tôi")
	@PreAuthorize("hasRole('MEMBER')")
	@GetMapping("/me")
	public PageResponse<BookingResponse> listMine(@AuthenticationPrincipal UserPrincipal principal,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return bookingService.listMine(principal.getId(), page, size);
	}

	@Operation(summary = "HLV xem lịch PT được đặt, từ ngày from đến hết ngày to (mặc định 7 ngày tới)")
	@PreAuthorize("hasRole('TRAINER')")
	@GetMapping("/trainer/me")
	public List<BookingResponse> listForTrainer(@AuthenticationPrincipal UserPrincipal principal,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		LocalDate start = from != null ? from : LocalDate.now();
		LocalDate end = to != null ? to : start.plusDays(6);
		LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();
		return bookingService.listForTrainer(principal.getId(), start.atStartOfDay(), endExclusive);
	}
}
