package com.duyanh.dto.fitnessclass;

import java.time.LocalDateTime;

import com.duyanh.entity.enums.ClassSessionStatus;

public record ClassSessionResponse(
		Long id,
		Long classId,
		String className,
		Long trainerId,
		String trainerName,
		String room,
		LocalDateTime startAt,
		LocalDateTime endAt,
		Integer capacity,
		Integer bookedCount,
		Integer availableSeats,
		ClassSessionStatus status) {
}
