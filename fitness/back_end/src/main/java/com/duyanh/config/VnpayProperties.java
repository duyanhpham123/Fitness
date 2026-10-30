package com.duyanh.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình app.vnpay.* trong application.properties.
 *
 * @param tmnCode     mã website (Terminal ID) VNPay cấp
 * @param hashSecret  chuỗi bí mật để ký / kiểm tra chữ ký HMAC-SHA512
 * @param returnUrl   VNPay chuyển trình duyệt về URL này sau khi thanh toán
 * @param feResultUrl trang kết quả của frontend, backend redirect tới sau khi xử lý return
 */
@ConfigurationProperties(prefix = "app.vnpay")
public record VnpayProperties(
		String tmnCode,
		String hashSecret,
		String payUrl,
		String returnUrl,
		String feResultUrl,
		int expireMinutes) {

	public boolean isConfigured() {
		return tmnCode != null && !tmnCode.isBlank() && hashSecret != null && !hashSecret.isBlank();
	}
}
