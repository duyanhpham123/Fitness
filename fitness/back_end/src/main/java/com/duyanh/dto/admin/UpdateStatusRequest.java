package com.duyanh.dto.admin;

import com.duyanh.entity.enums.UserStatus;

import jakarta.validation.constraints.NotNull;

/**
 * @param status ACTIVE (mở khóa) hoặc LOCKED (khóa)
 */
public record UpdateStatusRequest(

		@NotNull(message = "Chưa chọn trạng thái")
		UserStatus status) {
}
