package com.duyanh.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.booking.BookingResponse;
import com.duyanh.dto.report.AttendanceReport;
import com.duyanh.dto.report.NewMemberResponse;
import com.duyanh.dto.report.OverviewResponse;
import com.duyanh.dto.report.RevenueReport;
import com.duyanh.dto.report.TopPackageResponse;
import com.duyanh.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Số liệu dashboard. Doanh thu chỉ ADMIN xem; lịch hẹn trong ngày thì lễ tân (STAFF) cũng xem được.
 * Tham số ngày dạng yyyy-MM-dd, khoảng [from, to] tính trọn ngày.
 */
@Tag(name = "Report / Dashboard")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

	private final ReportService reportService;

	@Operation(summary = "4 chỉ số tổng quan, so với tháng trước")
	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping("/overview")
	public OverviewResponse overview() {
		return reportService.overview();
	}

	@Operation(summary = "Doanh thu theo ngày (mặc định 7 ngày gần nhất)")
	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping("/revenue")
	public RevenueReport revenue(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		LocalDate end = to != null ? to : LocalDate.now();
		return reportService.revenue(from != null ? from : end.minusDays(6), end);
	}

	@Operation(summary = "Gói tập bán chạy (mặc định tháng này)")
	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping("/packages/top")
	public List<TopPackageResponse> topPackages(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		LocalDate end = to != null ? to : LocalDate.now();
		return reportService.topPackages(from != null ? from : end.withDayOfMonth(1), end);
	}

	@Operation(summary = "Tỷ lệ đi tập và số lượt check-in theo ngày (mặc định tháng này)")
	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping("/attendance")
	public AttendanceReport attendance(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		LocalDate end = to != null ? to : LocalDate.now();
		return reportService.attendance(from != null ? from : end.withDayOfMonth(1), end);
	}

	@Operation(summary = "Hội viên đăng ký gần đây kèm gói tập")
	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping("/members/new")
	public List<NewMemberResponse> newMembers(@RequestParam(defaultValue = "5") int limit) {
		return reportService.newMembers(limit);
	}

	@Operation(summary = "Lịch hẹn trong 1 ngày (mặc định hôm nay)")
	@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
	@GetMapping("/bookings")
	public List<BookingResponse> bookings(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return reportService.bookingsOn(date != null ? date : LocalDate.now());
	}
}
