package com.duyanh.entity.converter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Đọc / ghi cột TIME dạng chuỗi "HH:mm:ss" để giữ nguyên giờ treo tường (vd. ca làm 06:00 - 11:00).
 *
 * Vì sao cần: hibernate.jdbc.time_zone=UTC (đúng cho DATETIME) cũng đổi múi giờ cho LocalTime,
 * quy đổi qua ngày 1970-01-01 nên 06:00 bị đọc thành 14:00 (giờ VN năm 1970 là +8).
 * MySQL tự chuyển chuỗi "HH:mm:ss" sang kiểu TIME nên cột trong DB vẫn là TIME.
 *
 * Dùng kèm @Column(columnDefinition = "TIME") để schema validation vẫn khớp với cột TIME.
 */
@Converter
public class WallClockTimeConverter implements AttributeConverter<LocalTime, String> {

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

	@Override
	public String convertToDatabaseColumn(LocalTime time) {
		return time == null ? null : time.format(FORMAT);
	}

	@Override
	public LocalTime convertToEntityAttribute(String value) {
		return value == null ? null : LocalTime.parse(value);
	}
}
