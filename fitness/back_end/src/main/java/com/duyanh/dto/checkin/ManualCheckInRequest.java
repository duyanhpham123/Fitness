package com.duyanh.dto.checkin;

import jakarta.validation.constraints.NotNull;

public record ManualCheckInRequest(

		@NotNull(message = "Chưa chọn hội viên")
		Long memberId) {
}
