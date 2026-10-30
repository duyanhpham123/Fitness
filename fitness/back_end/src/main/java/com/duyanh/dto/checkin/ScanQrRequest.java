package com.duyanh.dto.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScanQrRequest(

		@NotBlank(message = "Thiếu mã QR")
		@Size(max = 100, message = "Mã QR không hợp lệ")
		String qrToken) {
}
