package com.duyanh.dto.membership;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.duyanh.entity.enums.SubscriptionStatus;

/**
 * @param remainingFreezeDays số ngày bảo lưu còn được dùng
 */
public record SubscriptionResponse(
		Long id,
		Long packageId,
		String packageCode,
		String packageName,
		LocalDate startDate,
		LocalDate endDate,
		SubscriptionStatus status,
		BigDecimal pricePaid,
		Integer ptSessionsRemaining,
		LocalDate frozenFrom,
		LocalDate frozenUntil,
		Integer frozenDaysUsed,
		Integer remainingFreezeDays,
		LocalDateTime createdAt) {
}
