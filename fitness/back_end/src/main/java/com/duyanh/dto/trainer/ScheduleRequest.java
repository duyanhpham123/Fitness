package com.duyanh.dto.trainer;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Thay toàn bộ lịch làm việc hàng tuần. Danh sách rỗng = tạm nghỉ, không nhận lịch PT.
 */
public record ScheduleRequest(

		@NotNull(message = "Thiếu danh sách ca làm việc")
		@Size(max = 50, message = "Tối đa 50 ca làm việc")
		List<@Valid ScheduleItem> items) {
}
