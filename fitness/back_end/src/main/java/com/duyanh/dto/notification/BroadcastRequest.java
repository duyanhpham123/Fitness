package com.duyanh.dto.notification;

import com.duyanh.entity.enums.NotificationType;
import com.duyanh.entity.enums.RoleCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param role null = gửi tất cả tài khoản đang hoạt động
 */
public record BroadcastRequest(

		@NotBlank(message = "Tiêu đề không được để trống")
		@Size(max = 150, message = "Tiêu đề tối đa 150 ký tự")
		String title,

		@NotBlank(message = "Nội dung không được để trống")
		@Size(max = 1000, message = "Nội dung tối đa 1000 ký tự")
		String content,

		@NotNull(message = "Chưa chọn loại thông báo")
		NotificationType type,

		RoleCode role) {
}
