package com.duyanh.dto.workout;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param bmi cân nặng / chiều cao² (làm tròn 1 chữ số), null nếu thiếu 1 trong 2 số đo
 */
public record BodyMetricResponse(
		Long id,
		LocalDate measuredAt,
		BigDecimal weightKg,
		BigDecimal heightCm,
		BigDecimal bodyFatPct,
		BigDecimal muscleMassKg,
		BigDecimal bmi,
		String note) {
}
