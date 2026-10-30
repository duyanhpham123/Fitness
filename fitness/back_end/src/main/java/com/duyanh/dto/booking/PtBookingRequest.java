package com.duyanh.dto.booking;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param startAt phải trùng một khung giờ trong GET /api/trainers/{id}/slots
 */
public record PtBookingRequest(

		@NotNull(message = "Chưa chọn huấn luyện viên")
		Long trainerId,

		@NotNull(message = "Chưa chọn giờ tập")
		LocalDateTime startAt,

		@Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
		String note) {
}
