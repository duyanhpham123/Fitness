package com.duyanh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.duyanh.entity.Exercise;
import com.duyanh.entity.enums.MuscleGroup;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

	List<Exercise> findByMuscleGroupOrderByNameAsc(MuscleGroup muscleGroup);

	List<Exercise> findAllByOrderByMuscleGroupAscNameAsc();

	boolean existsByName(String name);
}
