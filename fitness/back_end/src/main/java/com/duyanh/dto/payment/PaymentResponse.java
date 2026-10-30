package com.duyanh.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.duyanh.entity.enums.PaymentMethod;
import com.duyanh.entity.enums.PaymentStatus;

public record PaymentResponse(
		Long id,
		String code,
		Long subscriptionId,
		BigDecimal amount,
		PaymentMethod method,
		PaymentStatus status,
		LocalDateTime paidAt,
		LocalDateTime createdAt) {
}
