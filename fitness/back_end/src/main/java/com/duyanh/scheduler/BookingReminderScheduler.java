package com.duyanh.scheduler;

import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.duyanh.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 20:00 mỗi tối (giờ Việt Nam): nhắc hội viên các lịch tập của ngày mai.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingReminderScheduler {

	private final NotificationService notificationService;

	@Scheduled(cron = "0 0 20 * * *", zone = "Asia/Ho_Chi_Minh")
	public void remindTomorrow() {
		LocalDate tomorrow = LocalDate.now().plusDays(1);
		int sent = notificationService.remindBookings(tomorrow);
		log.info("Booking reminder for {}: {} notifications", tomorrow, sent);
	}
}
