package com.duyanh.dto.workout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.duyanh.entity.enums.MuscleGroup;
import com.duyanh.entity.enums.WorkoutPlanStatus;

/**
 * Kế hoạch tập kèm toàn bộ ngày tập và bài tập (màn "Kế hoạch tập luyện").
 *
 * @param trainerId / trainerName null nếu hội viên tự soạn
 */
public record WorkoutPlanResponse(
		Long id,
		Long memberId,
		String memberName,
		Long trainerId,
		String trainerName,
		String name,
		String goal,
		LocalDate startDate,
		LocalDate endDate,
		WorkoutPlanStatus status,
		int completedDays,
		int totalDays,
		List<Day> days) {

	public record Day(
			Long id,
			Integer dayNumber,
			String title,
			LocalDateTime completedAt,
			List<Item> exercises) {
	}

	public record Item(
			Long id,
			Long exerciseId,
			String exerciseName,
			MuscleGroup muscleGroup,
			String imageUrl,
			Integer sortOrder,
			Integer sets,
			Integer reps,
			Integer durationSeconds,
			Integer restSeconds,
			BigDecimal weightKg,
			String note) {
	}
}
