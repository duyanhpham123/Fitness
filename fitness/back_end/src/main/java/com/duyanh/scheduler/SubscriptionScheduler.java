package com.duyanh.scheduler;

import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.repository.SubscriptionRepository;
import com.duyanh.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Chạy 00:05 mỗi ngày theo giờ Việt Nam.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

	// Nhắc gia hạn trước bao nhiêu ngày
	private static final int REMIND_BEFORE_DAYS = 3;

	private final SubscriptionRepository subscriptionRepository;
	private final NotificationService notificationService;

	@Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
	@Transactional
	public void runDaily() {
		LocalDate today = LocalDate.now();
		// Mở bảo lưu trước, để gói vừa hết bảo lưu được xét hết hạn ở bước sau
		int unfrozen = subscriptionRepository.unfreezeDue(today);
		int expired = subscriptionRepository.expireBefore(today);
		int reminded = notificationService.remindExpiring(today.plusDays(REMIND_BEFORE_DAYS));
		log.info("Subscription job {}: unfrozen={}, expired={}, reminded={}", today, unfrozen, expired, reminded);
	}
}
