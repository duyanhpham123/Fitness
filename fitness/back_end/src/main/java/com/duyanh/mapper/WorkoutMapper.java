package com.duyanh.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.duyanh.dto.workout.ExerciseResponse;
import com.duyanh.dto.workout.WorkoutPlanResponse;
import com.duyanh.entity.Exercise;
import com.duyanh.entity.WorkoutPlan;
import com.duyanh.entity.WorkoutPlanDay;
import com.duyanh.entity.WorkoutPlanExercise;

@Mapper
public interface WorkoutMapper {

	ExerciseResponse toResponse(Exercise exercise);

	@Mapping(target = "memberId", source = "member.id")
	@Mapping(target = "memberName", source = "member.fullName")
	@Mapping(target = "trainerId", source = "trainer.id")
	@Mapping(target = "trainerName", source = "trainer.user.fullName")
	@Mapping(target = "totalDays", expression = "java(plan.getDays().size())")
	@Mapping(target = "completedDays",
			expression = "java((int) plan.getDays().stream().filter(d -> d.getCompletedAt() != null).count())")
	WorkoutPlanResponse toResponse(WorkoutPlan plan);

	WorkoutPlanResponse.Day toDay(WorkoutPlanDay day);

	@Mapping(target = "exerciseId", source = "exercise.id")
	@Mapping(target = "exerciseName", source = "exercise.name")
	@Mapping(target = "muscleGroup", source = "exercise.muscleGroup")
	@Mapping(target = "imageUrl", source = "exercise.imageUrl")
	WorkoutPlanResponse.Item toItem(WorkoutPlanExercise item);
}
