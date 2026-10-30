package com.duyanh.event;

import java.time.LocalDateTime;

/**
 * Phát ra khi hội viên đặt hoặc hủy lịch, hoặc lịch bị hủy do buổi lớp bị hủy.
 *
 * @param target mô tả lịch, vd. "buổi PT với HLV Nguyễn Tuấn Anh" hoặc "lớp Zumba"
 * @param reason lý do hủy (khi phòng tập hủy buổi lớp), null nếu không có
 */
public record BookingChangedEvent(Long bookingId, Long memberId, String target, LocalDateTime startAt,
		boolean cancelled, String reason) {
}
