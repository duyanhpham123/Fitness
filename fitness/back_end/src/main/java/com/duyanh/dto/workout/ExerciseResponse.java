package com.duyanh.dto.workout;

import com.duyanh.entity.enums.MuscleGroup;

public record ExerciseResponse(
		Long id,
		String name,
		MuscleGroup muscleGroup,
		String equipment,
		String description,
		String imageUrl,
		String videoUrl) {
}
