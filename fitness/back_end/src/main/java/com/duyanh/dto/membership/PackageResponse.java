package com.duyanh.dto.membership;

import java.math.BigDecimal;

public record PackageResponse(
		Long id,
		String code,
		String name,
		String description,
		Integer durationDays,
		BigDecimal price,
		Integer ptSessions,
		Integer maxFreezeDays,
		boolean active) {
}
