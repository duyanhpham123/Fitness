package com.duyanh.dto.membership;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Tạo / sửa gói tập (ADMIN).
 */
public record PackageRequest(

		@NotBlank(message = "Mã gói không được để trống")
		@Size(max = 30, message = "Mã gói tối đa 30 ký tự")
		@Pattern(regexp = "^[A-Z0-9_]+$", message = "Mã gói chỉ gồm chữ in hoa, số và dấu _")
		String code,

		@NotBlank(message = "Tên gói không được để trống")
		@Size(max = 100, message = "Tên gói tối đa 100 ký tự")
		String name,

		@Size(max = 500, message = "Mô tả tối đa 500 ký tự")
		String description,

		@NotNull(message = "Thời hạn không được để trống")
		@Min(value = 1, message = "Thời hạn tối thiểu 1 ngày")
		@Max(value = 3650, message = "Thời hạn tối đa 3650 ngày")
		Integer durationDays,

		@NotNull(message = "Giá không được để trống")
		@DecimalMin(value = "0", message = "Giá không được âm")
		@Digits(integer = 13, fraction = 0, message = "Giá không hợp lệ")
		BigDecimal price,

		@NotNull(message = "Số buổi PT không được để trống")
		@Min(value = 0, message = "Số buổi PT không được âm")
		Integer ptSessions,

		@NotNull(message = "Số ngày bảo lưu không được để trống")
		@Min(value = 0, message = "Số ngày bảo lưu không được âm")
		Integer maxFreezeDays,

		// null = giữ mặc định (đang bán)
		Boolean active) {
}
