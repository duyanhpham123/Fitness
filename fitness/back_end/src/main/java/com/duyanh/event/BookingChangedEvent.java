package com.duyanh.event;

import java.time.LocalDateTime;

/**
 * Phát ra khi hội viên đặt hoặc hủy lịch.
 *
 * @param target mô tả lịch, vd. "buổi PT với HLV Nguyễn Tuấn Anh" hoặc "lớp Zumba"
 */
public record BookingChangedEvent(Long bookingId, Long memberId, String target, LocalDateTime startAt,
		boolean cancelled) {
}
