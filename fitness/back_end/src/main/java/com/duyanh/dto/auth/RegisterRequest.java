package com.duyanh.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

		@NotBlank(message = "Email không được để trống")
		@Email(message = "Email không hợp lệ")
		@Size(max = 150, message = "Email tối đa 150 ký tự")
		String email,

		// BCrypt chỉ dùng 72 byte đầu nên giới hạn 72
		@NotBlank(message = "Mật khẩu không được để trống")
		@Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự")
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Mật khẩu phải có cả chữ và số")
		String password,

		@NotBlank(message = "Họ tên không được để trống")
		@Size(max = 100, message = "Họ tên tối đa 100 ký tự")
		String fullName,

		// Không bắt buộc; nếu có phải là số di động VN 10 chữ số
		@Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại không hợp lệ")
		String phone) {
}
