package com.duyanh.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "workout_plan_exercises")
public class WorkoutPlanExercise {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_day_id", nullable = false)
	private WorkoutPlanDay planDay;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "exercise_id", nullable = false)
	private Exercise exercise;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 1;

	private Integer sets;

	private Integer reps;

	// Bài tính theo thời gian (plank, chạy bộ)
	@Column(name = "duration_seconds")
	private Integer durationSeconds;

	@Column(name = "rest_seconds")
	private Integer restSeconds;

	@Column(name = "weight_kg", precision = 5, scale = 1)
	private BigDecimal weightKg;

	@Column(length = 255)
	private String note;
}
