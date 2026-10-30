package com.duyanh.dto.trainer;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Một ca làm việc lặp lại hàng tuần. Dùng cho cả request và response.
 *
 * @param dayOfWeek 1 = Thứ 2 ... 7 = Chủ nhật
 */
public record ScheduleItem(

		@NotNull(message = "Thiếu thứ trong tuần")
		@Min(value = 1, message = "Thứ trong tuần từ 1 (Thứ 2) đến 7 (Chủ nhật)")
		@Max(value = 7, message = "Thứ trong tuần từ 1 (Thứ 2) đến 7 (Chủ nhật)")
		Integer dayOfWeek,

		@NotNull(message = "Thiếu giờ bắt đầu")
		LocalTime startTime,

		@NotNull(message = "Thiếu giờ kết thúc")
		LocalTime endTime) {
}
