package com.duyanh.dto.trainer;

import java.time.LocalDateTime;

/**
 * Khung giờ PT còn trống, dùng startAt để gọi POST /api/bookings/pt.
 */
public record SlotResponse(LocalDateTime startAt, LocalDateTime endAt) {
}
