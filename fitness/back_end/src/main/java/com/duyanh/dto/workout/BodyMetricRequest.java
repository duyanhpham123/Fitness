package com.duyanh.dto.workout;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Ghi chỉ số cơ thể. Cùng 1 ngày ghi lại thì cập nhật dòng cũ.
 *
 * @param measuredAt null = hôm nay
 */
public record BodyMetricRequest(

		@PastOrPresent(message = "Ngày đo không được ở tương lai")
		LocalDate measuredAt,

		@DecimalMin(value = "20", message = "Cân nặng từ 20 kg")
		@DecimalMax(value = "400", message = "Cân nặng tối đa 400 kg")
		BigDecimal weightKg,

		@DecimalMin(value = "50", message = "Chiều cao từ 50 cm")
		@DecimalMax(value = "260", message = "Chiều cao tối đa 260 cm")
		BigDecimal heightCm,

		@DecimalMin(value = "1", message = "Tỷ lệ mỡ từ 1%")
		@DecimalMax(value = "80", message = "Tỷ lệ mỡ tối đa 80%")
		BigDecimal bodyFatPct,

		@DecimalMin(value = "5", message = "Khối lượng cơ từ 5 kg")
		@DecimalMax(value = "200", message = "Khối lượng cơ tối đa 200 kg")
		BigDecimal muscleMassKg,

		@Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
		String note) {
}
