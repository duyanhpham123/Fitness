package com.duyanh.dto.trainer;

import java.math.BigDecimal;

/**
 * @param id chính là user id của HLV
 */
public record TrainerResponse(
		Long id,
		String fullName,
		String avatarUrl,
		String specialization,
		String bio,
		Integer experienceYears,
		String certifications,
		BigDecimal sessionPrice,
		BigDecimal rating) {
}
