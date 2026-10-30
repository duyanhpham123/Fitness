package com.duyanh.dto.user;

import java.time.LocalDate;

import com.duyanh.entity.enums.Gender;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Người dùng tự sửa hồ sơ. Email không đổi được ở đây (là tên đăng nhập).
 */
public record UpdateProfileRequest(

		@NotBlank(message = "Họ tên không được để trống")
		@Size(max = 100, message = "Họ tên tối đa 100 ký tự")
		String fullName,

		@Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại không hợp lệ")
		String phone,

		Gender gender,

		@Past(message = "Ngày sinh phải ở quá khứ")
		LocalDate dateOfBirth,

		@Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
		String address,

		@Size(max = 500, message = "Link ảnh đại diện tối đa 500 ký tự")
		String avatarUrl) {
}
