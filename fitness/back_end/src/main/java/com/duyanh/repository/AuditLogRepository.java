package com.duyanh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.duyanh.entity.AuditLog;

/**
 * JpaSpecificationExecutor để lọc động theo actor / action / khoảng thời gian.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
}
