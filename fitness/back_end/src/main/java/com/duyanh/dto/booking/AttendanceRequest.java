package com.duyanh.dto.booking;

import com.duyanh.entity.enums.BookingStatus;

import jakarta.validation.constraints.NotNull;

/**
 * @param status COMPLETED (có đến tập) hoặc NO_SHOW (vắng mặt)
 */
public record AttendanceRequest(

		@NotNull(message = "Chưa chọn trạng thái")
		BookingStatus status) {
}
