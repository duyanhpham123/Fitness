package com.duyanh.dto.auth;

import com.duyanh.dto.user.UserResponse;

/**
 * @param expiresIn số giây access token còn hiệu lực
 */
public record AuthResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresIn,
		UserResponse user) {
}
