package com.duyanh.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.duyanh.entity.Role;
import com.duyanh.entity.enums.RoleCode;

public interface RoleRepository extends JpaRepository<Role, Integer> {

	Optional<Role> findByCode(RoleCode code);
}
