package com.duyanh.dto.booking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClassBookingRequest(

		@NotNull(message = "Chưa chọn buổi học")
		Long classSessionId,

		@Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
		String note) {
}
