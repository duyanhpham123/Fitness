package com.duyanh.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.fitnessclass.CancelSessionRequest;
import com.duyanh.dto.fitnessclass.ClassRequest;
import com.duyanh.dto.fitnessclass.ClassResponse;
import com.duyanh.dto.fitnessclass.ClassSessionRequest;
import com.duyanh.dto.fitnessclass.ClassSessionResponse;
import com.duyanh.service.ClassService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Class - Lớp học nhóm")
@RestController
@RequiredArgsConstructor
public class ClassController {

	private final ClassService classService;

	@Operation(summary = "Danh sách lớp học đang mở (không cần đăng nhập)")
	@GetMapping("/api/classes")
	public List<ClassResponse> listClasses() {
		return classService.listClasses();
	}

	@Operation(summary = "Tạo lớp học (ADMIN, STAFF)")
	@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
	@PostMapping("/api/classes")
	@ResponseStatus(HttpStatus.CREATED)
	public ClassResponse createClass(@Valid @RequestBody ClassRequest req) {
		return classService.createClass(req);
	}

	@Operation(summary = "Các buổi học trong 1 ngày kèm số chỗ còn trống (mặc định hôm nay)")
	@GetMapping("/api/class-sessions")
	public List<ClassSessionResponse> listSessions(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return classService.listSessions(date != null ? date : LocalDate.now());
	}

	@Operation(summary = "Mở buổi học (ADMIN, STAFF); HLV không được trùng giờ")
	@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
	@PostMapping("/api/class-sessions")
	@ResponseStatus(HttpStatus.CREATED)
	public ClassSessionResponse createSession(@Valid @RequestBody ClassSessionRequest req) {
		return classService.createSession(req);
	}

	@Operation(summary = "Hủy buổi học chưa diễn ra (ADMIN, STAFF); hủy luôn lịch đã đặt và báo cho hội viên")
	@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
	@PostMapping("/api/class-sessions/{id}/cancel")
	public ClassSessionResponse cancelSession(@PathVariable Long id, @Valid @RequestBody CancelSessionRequest req) {
		return classService.cancelSession(id, req.reason());
	}
}
