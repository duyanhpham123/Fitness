package com.duyanh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.workout.BodyMetricRequest;
import com.duyanh.dto.workout.BodyMetricResponse;
import com.duyanh.security.UserPrincipal;
import com.duyanh.service.BodyMetricService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Workout - Chỉ số cơ thể")
@RestController
@RequestMapping("/api/body-metrics/me")
@PreAuthorize("hasRole('MEMBER')")
@RequiredArgsConstructor
public class BodyMetricController {

	private final BodyMetricService bodyMetricService;

	@Operation(summary = "Lịch sử chỉ số cơ thể (cũ -> mới) để vẽ biểu đồ tiến độ")
	@GetMapping
	public List<BodyMetricResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
		return bodyMetricService.listMine(principal.getId());
	}

	@Operation(summary = "Ghi chỉ số của 1 ngày (mặc định hôm nay); ngày đã có thì cập nhật")
	@PutMapping
	public BodyMetricResponse record(@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody BodyMetricRequest req) {
		return bodyMetricService.record(principal.getId(), req);
	}
}
