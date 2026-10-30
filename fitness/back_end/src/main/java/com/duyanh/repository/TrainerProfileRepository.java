package com.duyanh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.duyanh.entity.TrainerProfile;

public interface TrainerProfileRepository extends JpaRepository<TrainerProfile, Long> {

	@EntityGraph(attributePaths = "user")
	List<TrainerProfile> findByActiveTrue();

	@EntityGraph(attributePaths = "user")
	Optional<TrainerProfile> findByIdAndActiveTrue(Long id);
}
