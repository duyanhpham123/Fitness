package com.duyanh.dto.fitnessclass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason gửi kèm trong thông báo cho các hội viên đã đặt chỗ
 */
public record CancelSessionRequest(

		@NotBlank(message = "Cần nhập lý do hủy buổi học")
		@Size(max = 200, message = "Lý do tối đa 200 ký tự")
		String reason) {
}
