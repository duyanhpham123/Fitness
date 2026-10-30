package com.duyanh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.duyanh.entity.FitnessClass;

public interface FitnessClassRepository extends JpaRepository<FitnessClass, Long> {

	List<FitnessClass> findByActiveTrueOrderByNameAsc();

	boolean existsByName(String name);
}
