package com.duyanh.dto.booking;

import java.time.LocalDateTime;

import com.duyanh.entity.enums.BookingStatus;
import com.duyanh.entity.enums.BookingType;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Các field trainer* có giá trị khi type = PT, class* / room khi type = CLASS.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(
		Long id,
		BookingType type,
		BookingStatus status,
		LocalDateTime startAt,
		LocalDateTime endAt,
		Long memberId,
		String memberName,
		Long trainerId,
		String trainerName,
		Long classSessionId,
		String className,
		String room,
		String note,
		LocalDateTime cancelledAt,
		String cancelReason,
		LocalDateTime createdAt) {
}
