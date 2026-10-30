package com.duyanh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.duyanh.entity.WorkoutPlan;
import com.duyanh.entity.enums.WorkoutPlanStatus;

/**
 * Ngày tập / bài tập của kế hoạch được load lazy theo lô (hibernate.default_batch_fetch_size),
 * tránh N+1 khi trả về cả cây kế hoạch.
 */
public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {

	List<WorkoutPlan> findByMemberIdAndStatusInOrderByCreatedAtDesc(Long memberId, List<WorkoutPlanStatus> statuses);

	List<WorkoutPlan> findByTrainerIdOrderByCreatedAtDesc(Long trainerId);
}
