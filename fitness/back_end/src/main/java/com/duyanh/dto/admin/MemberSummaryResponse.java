package com.duyanh.dto.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.duyanh.entity.enums.SubscriptionStatus;
import com.duyanh.entity.enums.UserStatus;

/**
 * Một dòng trong màn "Danh sách hội viên".
 *
 * @param packageName / subscriptionStatus / subscriptionEndDate của gói gần nhất, null nếu chưa từng mua
 */
public record MemberSummaryResponse(
		Long id,
		String fullName,
		String email,
		String phone,
		String avatarUrl,
		UserStatus status,
		String packageName,
		SubscriptionStatus subscriptionStatus,
		LocalDate subscriptionEndDate,
		LocalDateTime createdAt) {
}
