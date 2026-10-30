package com.duyanh.dto.notification;

import java.time.LocalDateTime;

import com.duyanh.entity.enums.NotificationChannel;
import com.duyanh.entity.enums.NotificationType;

/**
 * @param referenceType / referenceId đối tượng liên quan (vd. BOOKING + id) để frontend mở chi tiết
 */
public record NotificationResponse(
		Long id,
		NotificationType type,
		String title,
		String content,
		NotificationChannel channel,
		String referenceType,
		Long referenceId,
		boolean read,
		LocalDateTime readAt,
		LocalDateTime createdAt) {
}
