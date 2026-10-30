package com.duyanh.dto.checkin;

import java.time.LocalDateTime;

import com.duyanh.entity.enums.CheckInMethod;

/**
 * @param packageName gói dùng để vào tập
 * @param staffName   lễ tân thực hiện check-in
 */
public record CheckInResponse(
		Long id,
		Long memberId,
		String memberName,
		Long subscriptionId,
		String packageName,
		LocalDateTime checkedInAt,
		LocalDateTime checkedOutAt,
		CheckInMethod method,
		String staffName) {
}
