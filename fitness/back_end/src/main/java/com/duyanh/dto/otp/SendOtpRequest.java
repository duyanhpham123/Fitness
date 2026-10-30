package com.duyanh.dto.otp;

import com.duyanh.entity.enums.OtpPurpose;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Gửi lại mã OTP (VERIFY_EMAIL hoặc RESET_PASSWORD).
 */
public record SendOtpRequest(

		@NotBlank(message = "Email không được để trống")
		@Email(message = "Email không hợp lệ")
		String email,

		@NotNull(message = "Mục đích không được để trống")
		OtpPurpose purpose) {
}
