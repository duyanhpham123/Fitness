package com.duyanh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.duyanh.entity.MembershipPackage;

public interface MembershipPackageRepository extends JpaRepository<MembershipPackage, Long> {

	Optional<MembershipPackage> findByCode(String code);

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, Long id);

	List<MembershipPackage> findByActiveTrueOrderByDurationDaysAsc();

	List<MembershipPackage> findAllByOrderByDurationDaysAsc();
}
