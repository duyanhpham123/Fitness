package com.duyanh.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.duyanh.entity.enums.PaymentMethod;
import com.duyanh.entity.enums.PaymentStatus;

/**
 * @param memberName  người mua (lễ tân cần để đối chiếu khi thu tiền)
 * @param packageName gói được mua, null nếu giao dịch không gắn với gói
 */
public record PaymentResponse(
		Long id,
		String code,
		Long subscriptionId,
		Long memberId,
		String memberName,
		String packageName,
		BigDecimal amount,
		PaymentMethod method,
		PaymentStatus status,
		LocalDateTime paidAt,
		LocalDateTime createdAt) {
}
