package com.duyanh.dto.report;

import java.time.LocalDateTime;

/**
 * @param packageName gói gần nhất, null nếu chưa mua gói
 */
public record NewMemberResponse(Long id, String fullName, String avatarUrl, String packageName,
		LocalDateTime createdAt) {
}
