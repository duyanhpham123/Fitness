package com.duyanh.dto.audit;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * @param actorId null = hệ thống (job, IPN)
 */
public record AuditLogResponse(
		Long id,
		Long actorId,
		String actorEmail,
		String action,
		String entityType,
		Long entityId,
		Map<String, Object> oldValue,
		Map<String, Object> newValue,
		String ipAddress,
		String userAgent,
		LocalDateTime createdAt) {
}
