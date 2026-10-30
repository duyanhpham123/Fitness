package com.duyanh.dto.fitnessclass;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Mở một buổi học. Giờ kết thúc = startAt + thời lượng của lớp.
 *
 * @param capacity null = lấy sức chứa mặc định của lớp
 */
public record ClassSessionRequest(

		@NotNull(message = "Chưa chọn lớp")
		Long classId,

		@NotNull(message = "Chưa chọn huấn luyện viên")
		Long trainerId,

		@NotNull(message = "Thiếu giờ bắt đầu")
		@Future(message = "Giờ bắt đầu phải ở tương lai")
		LocalDateTime startAt,

		@Size(max = 50, message = "Tên phòng tối đa 50 ký tự")
		String room,

		@Min(value = 1, message = "Sức chứa tối thiểu 1")
		@Max(value = 500, message = "Sức chứa tối đa 500")
		Integer capacity) {
}
