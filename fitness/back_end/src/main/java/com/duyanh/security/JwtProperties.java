package com.duyanh.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Đọc cấu hình app.jwt.* trong application.properties.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		String secret,
		long accessExpirationMs,
		long refreshExpirationMs,
		long refreshExpirationRememberMs) {
}
