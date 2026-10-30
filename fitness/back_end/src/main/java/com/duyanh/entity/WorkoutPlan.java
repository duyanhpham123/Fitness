package com.duyanh.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.duyanh.entity.enums.WorkoutPlanStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kế hoạch tập. Lưu cả cây WorkoutPlan -> days -> exercises chỉ bằng 1 lần save().
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "workout_plans")
public class WorkoutPlan extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private User member;

	// NULL = hội viên tự tạo
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "trainer_id")
	private TrainerProfile trainer;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(length = 255)
	private String goal;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private WorkoutPlanStatus status = WorkoutPlanStatus.ACTIVE;

	@OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("dayNumber ASC")
	private List<WorkoutPlanDay> days = new ArrayList<>();

	public void addDay(WorkoutPlanDay day) {
		day.setPlan(this);
		days.add(day);
	}
}
