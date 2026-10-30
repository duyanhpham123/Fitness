package com.duyanh.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.duyanh.entity.BodyMetric;

public interface BodyMetricRepository extends JpaRepository<BodyMetric, Long> {

	List<BodyMetric> findByUserIdOrderByMeasuredAtAsc(Long userId);

	Optional<BodyMetric> findByUserIdAndMeasuredAt(Long userId, LocalDate measuredAt);
}
