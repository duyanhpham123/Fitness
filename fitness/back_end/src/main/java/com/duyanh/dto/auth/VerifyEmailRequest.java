package com.duyanh.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(

		@NotBlank(message = "Email không được để trống")
		@Email(message = "Email không hợp lệ")
		String email,

		@NotBlank(message = "Mã OTP không được để trống")
		@Pattern(regexp = "^\\d{6}$", message = "Mã OTP gồm 6 chữ số")
		String otp) {
}
