package com.duyanh.event;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Phát ra khi một giao dịch có kết quả (thành công hoặc thất bại).
 *
 * @param packageName / startDate / endDate của gói được kích hoạt, null nếu thất bại
 */
public record PaymentResultEvent(Long paymentId, Long userId, String paymentCode, BigDecimal amount,
		boolean success, String packageName, LocalDate startDate, LocalDate endDate) {
}
