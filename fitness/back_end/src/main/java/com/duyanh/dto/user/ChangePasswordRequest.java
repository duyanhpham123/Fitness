package com.duyanh.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

		@NotBlank(message = "Chưa nhập mật khẩu hiện tại")
		String currentPassword,

		@NotBlank(message = "Mật khẩu mới không được để trống")
		@Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự")
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Mật khẩu phải có cả chữ và số")
		String newPassword) {
}
