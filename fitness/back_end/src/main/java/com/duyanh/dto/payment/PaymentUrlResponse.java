package com.duyanh.dto.payment;

import java.time.LocalDateTime;

/**
 * @param paymentUrl frontend chuyển trình duyệt (window.location) tới link này
 * @param expiresAt  link hết hạn sau thời điểm này
 */
public record PaymentUrlResponse(String paymentUrl, LocalDateTime expiresAt) {
}
