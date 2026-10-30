package com.duyanh.dto.admin;

import com.duyanh.entity.enums.RoleCode;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ADMIN tạo tài khoản (nhân viên, HLV, hội viên tại quầy). Tài khoản được kích hoạt ngay.
 *
 * @param specialization chỉ dùng khi role = TRAINER
 */
public record CreateUserRequest(

		@NotBlank(message = "Email không được để trống")
		@Email(message = "Email không hợp lệ")
		@Size(max = 150, message = "Email tối đa 150 ký tự")
		String email,

		@NotBlank(message = "Mật khẩu không được để trống")
		@Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự")
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Mật khẩu phải có cả chữ và số")
		String password,

		@NotBlank(message = "Họ tên không được để trống")
		@Size(max = 100, message = "Họ tên tối đa 100 ký tự")
		String fullName,

		@Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại không hợp lệ")
		String phone,

		@NotNull(message = "Chưa chọn vai trò")
		RoleCode role,

		@Size(max = 150, message = "Chuyên môn tối đa 150 ký tự")
		String specialization) {
}
