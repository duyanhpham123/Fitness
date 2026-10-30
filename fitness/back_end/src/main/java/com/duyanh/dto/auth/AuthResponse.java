package com.duyanh.dto.auth;

import java.time.LocalDateTime;

import com.duyanh.dto.user.UserResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param refreshToken          null khi chạy production (token chỉ nằm trong cookie HttpOnly)
 * @param expiresIn             số giây access token còn hiệu lực
 * @param refreshTokenExpiresAt hết hạn phiên đăng nhập (dùng làm thời hạn cookie)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresIn,
		LocalDateTime refreshTokenExpiresAt,
		UserResponse user) {

	public AuthResponse withoutRefreshToken() {
		return new AuthResponse(accessToken, null, tokenType, expiresIn, refreshTokenExpiresAt, user);
	}
}
