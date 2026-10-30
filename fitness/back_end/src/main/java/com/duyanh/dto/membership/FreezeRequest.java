package com.duyanh.dto.membership;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Bảo lưu gói từ hôm nay trong N ngày. Ngày kết thúc gói được lùi thêm N ngày.
 */
public record FreezeRequest(

		@NotNull(message = "Số ngày bảo lưu không được để trống")
		@Min(value = 1, message = "Bảo lưu tối thiểu 1 ngày")
		@Max(value = 365, message = "Bảo lưu tối đa 365 ngày")
		Integer days) {
}
