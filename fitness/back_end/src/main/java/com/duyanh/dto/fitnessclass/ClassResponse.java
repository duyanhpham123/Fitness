package com.duyanh.dto.fitnessclass;

public record ClassResponse(
		Long id,
		String name,
		String description,
		Integer durationMinutes,
		Integer defaultCapacity,
		String imageUrl,
		boolean active) {
}
