package com.duyanh.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

		@NotBlank(message = "Email không được để trống")
		@Email(message = "Email không hợp lệ")
		String email,

		@NotBlank(message = "Mã OTP không được để trống")
		@Pattern(regexp = "^\\d{6}$", message = "Mã OTP gồm 6 chữ số")
		String otp,

		@NotBlank(message = "Mật khẩu không được để trống")
		@Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự")
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Mật khẩu phải có cả chữ và số")
		String newPassword) {
}
