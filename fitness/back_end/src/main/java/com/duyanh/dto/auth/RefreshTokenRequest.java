package com.duyanh.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Dùng cho cả /api/auth/refresh và /api/auth/logout.
 */
public record RefreshTokenRequest(

		@NotBlank(message = "Refresh token không được để trống")
		String refreshToken) {
}
