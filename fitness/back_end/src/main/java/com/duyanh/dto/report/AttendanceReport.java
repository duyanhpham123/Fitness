package com.duyanh.dto.report;

import java.time.LocalDate;
import java.util.List;

/**
 * @param activeMembers   hội viên có gói còn hiệu lực trong khoảng thời gian
 * @param attendedMembers hội viên khác nhau đã check-in ít nhất 1 lần
 * @param rate            attendedMembers / activeMembers (%), null nếu không có hội viên nào
 * @param days            số lượt check-in mỗi ngày
 */
public record AttendanceReport(long activeMembers, long attendedMembers, Double rate, List<DailyCount> days) {

	public record DailyCount(LocalDate date, long count) {
	}
}
