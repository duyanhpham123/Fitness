package com.duyanh.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "workout_plan_days")
public class WorkoutPlanDay {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_id", nullable = false)
	private WorkoutPlan plan;

	@Column(name = "day_number", nullable = false)
	private Integer dayNumber;

	// Ví dụ: "Ngực + Tay sau"
	@Column(nullable = false, length = 150)
	private String title;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@OneToMany(mappedBy = "planDay", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder ASC")
	private List<WorkoutPlanExercise> exercises = new ArrayList<>();

	public void addExercise(WorkoutPlanExercise exercise) {
		exercise.setPlanDay(this);
		exercises.add(exercise);
	}
}
