package com.duyanh.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình app.booking.* trong application.properties.
 *
 * @param ptSlotMinutes     độ dài 1 buổi PT (phút), lịch làm việc của HLV được chia theo bước này
 * @param cancelBeforeHours chỉ được hủy lịch trước giờ tập ít nhất bấy nhiêu giờ
 */
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(int ptSlotMinutes, int cancelBeforeHours) {
}
