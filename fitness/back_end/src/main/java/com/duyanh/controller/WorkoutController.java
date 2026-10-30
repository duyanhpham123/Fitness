package com.duyanh.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.workout.ExerciseRequest;
import com.duyanh.dto.workout.ExerciseResponse;
import com.duyanh.dto.workout.WorkoutPlanRequest;
import com.duyanh.dto.workout.WorkoutPlanResponse;
import com.duyanh.entity.enums.MuscleGroup;
import com.duyanh.security.UserPrincipal;
import com.duyanh.service.WorkoutService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Workout")
@RestController
@RequiredArgsConstructor
public class WorkoutController {

	private final WorkoutService workoutService;

	@Operation(summary = "Thư viện bài tập, lọc theo nhóm cơ")
	@GetMapping("/api/exercises")
	public List<ExerciseResponse> listExercises(@RequestParam(required = false) MuscleGroup muscleGroup) {
		return workoutService.listExercises(muscleGroup);
	}

	@Operation(summary = "Thêm bài tập vào thư viện (ADMIN, TRAINER)")
	@PreAuthorize("hasAnyRole('ADMIN', 'TRAINER')")
	@PostMapping("/api/exercises")
	@ResponseStatus(HttpStatus.CREATED)
	public ExerciseResponse createExercise(@Valid @RequestBody ExerciseRequest req) {
		return workoutService.createExercise(req);
	}

	@Operation(summary = "Kế hoạch tập của tôi (đang tập và đã hoàn thành)")
	@PreAuthorize("hasRole('MEMBER')")
	@GetMapping("/api/workout-plans/me")
	public List<WorkoutPlanResponse> listMine(@AuthenticationPrincipal UserPrincipal principal) {
		return workoutService.listMine(principal.getId());
	}

	@Operation(summary = "Các kế hoạch HLV đã soạn")
	@PreAuthorize("hasRole('TRAINER')")
	@GetMapping("/api/workout-plans/trainer/me")
	public List<WorkoutPlanResponse> listCreated(@AuthenticationPrincipal UserPrincipal principal) {
		return workoutService.listCreatedByTrainer(principal.getId());
	}

	@Operation(summary = "Chi tiết kế hoạch (hội viên sở hữu, HLV soạn, ADMIN)")
	@GetMapping("/api/workout-plans/{id}")
	public WorkoutPlanResponse get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
		return workoutService.get(id, principal);
	}

	@Operation(summary = "Tạo kế hoạch: HLV soạn cho hội viên (memberId) hoặc hội viên tự soạn")
	@PreAuthorize("hasAnyRole('TRAINER', 'MEMBER')")
	@PostMapping("/api/workout-plans")
	@ResponseStatus(HttpStatus.CREATED)
	public WorkoutPlanResponse create(@Valid @RequestBody WorkoutPlanRequest req,
			@AuthenticationPrincipal UserPrincipal principal) {
		return workoutService.createPlan(req, principal);
	}

	@Operation(summary = "Thay toàn bộ nội dung kế hoạch (chỉ người soạn)")
	@PreAuthorize("hasAnyRole('TRAINER', 'MEMBER')")
	@PutMapping("/api/workout-plans/{id}")
	public WorkoutPlanResponse update(@PathVariable Long id, @Valid @RequestBody WorkoutPlanRequest req,
			@AuthenticationPrincipal UserPrincipal principal) {
		return workoutService.updatePlan(id, req, principal);
	}

	@Operation(summary = "Đánh dấu đã tập xong 1 ngày")
	@PreAuthorize("hasRole('MEMBER')")
	@PostMapping("/api/workout-plans/{id}/days/{dayId}/complete")
	public WorkoutPlanResponse completeDay(@PathVariable Long id, @PathVariable Long dayId,
			@AuthenticationPrincipal UserPrincipal principal) {
		return workoutService.completeDay(id, dayId, principal.getId());
	}
}
