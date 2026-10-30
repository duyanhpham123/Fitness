package com.duyanh.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.trainer.ScheduleItem;
import com.duyanh.dto.trainer.ScheduleRequest;
import com.duyanh.dto.trainer.SlotResponse;
import com.duyanh.dto.trainer.TrainerProfileRequest;
import com.duyanh.dto.trainer.TrainerDetailResponse;
import com.duyanh.dto.trainer.TrainerResponse;
import com.duyanh.security.UserPrincipal;
import com.duyanh.service.TrainerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Trainer")
@RestController
@RequestMapping("/api/trainers")
@RequiredArgsConstructor
public class TrainerController {

	private final TrainerService trainerService;

	@Operation(summary = "Danh sách huấn luyện viên (không cần đăng nhập)")
	@GetMapping
	public List<TrainerResponse> list() {
		return trainerService.list();
	}

	@Operation(summary = "Hồ sơ + lịch làm việc hàng tuần của huấn luyện viên")
	@GetMapping("/{id}")
	public TrainerDetailResponse get(@PathVariable Long id) {
		return trainerService.get(id);
	}

	@Operation(summary = "Khung giờ PT còn trống trong 1 ngày (yyyy-MM-dd)")
	@GetMapping("/{id}/slots")
	public List<SlotResponse> slots(@PathVariable Long id,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return trainerService.availableSlots(id, date);
	}

	@Operation(summary = "HLV tự sửa hồ sơ (chuyên môn, giới thiệu, kinh nghiệm, chứng chỉ)")
	@PreAuthorize("hasRole('TRAINER')")
	@PutMapping("/me")
	public TrainerResponse updateMyProfile(@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody TrainerProfileRequest req) {
		return trainerService.updateProfile(principal.getId(), req);
	}

	@Operation(summary = "HLV cập nhật lịch làm việc hàng tuần (thay toàn bộ)")
	@PreAuthorize("hasRole('TRAINER')")
	@PutMapping("/me/schedule")
	public List<ScheduleItem> replaceMySchedule(@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody ScheduleRequest req) {
		return trainerService.replaceSchedule(principal.getId(), req.items());
	}
}
