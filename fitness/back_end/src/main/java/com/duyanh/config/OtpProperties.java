package com.duyanh.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình app.otp.* trong application.properties.
 *
 * @param exposeInResponse chỉ bật khi dev: trả mã OTP trong response (field devOtp) để test không cần email
 */
@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(
		long expirationSeconds,
		long resendCooldownSeconds,
		int maxAttempts,
		int maxSendsPerHour,
		boolean exposeInResponse) {
}
