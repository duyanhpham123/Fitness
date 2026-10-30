package com.duyanh.event;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.duyanh.entity.enums.NotificationType;
import com.duyanh.service.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * Chuyển sự kiện nghiệp vụ thành thông báo cho người dùng.
 *
 * Dùng @EventListener (đồng bộ, chung transaction): thông báo được lưu cùng lúc với thao tác,
 * thao tác rollback thì thông báo cũng không còn. Email được gửi riêng sau khi commit
 * (xem NotificationService).
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final NotificationService notificationService;

	@EventListener
	public void onBookingChanged(BookingChangedEvent e) {
		if (e.cancelled()) {
			String reason = e.reason() == null ? "" : " Lý do: " + e.reason();
			// Phòng tập chủ động hủy (có lý do) thì gửi thêm email để hội viên không đến nhầm
			notificationService.notify(e.memberId(), NotificationType.BOOKING, "Đã hủy lịch tập",
					"Lịch %s lúc %s đã được hủy.%s".formatted(e.target(), e.startAt().format(TIME), reason),
					"BOOKING", e.bookingId(), e.reason() != null);
		} else {
			notificationService.notify(e.memberId(), NotificationType.BOOKING, "Đặt lịch thành công",
					"Bạn đã đặt %s lúc %s.".formatted(e.target(), e.startAt().format(TIME)),
					"BOOKING", e.bookingId(), false);
		}
	}

	@EventListener
	public void onPaymentResult(PaymentResultEvent e) {
		if (e.success()) {
			String validity = e.startDate() == null ? ""
					: " Gói có hiệu lực từ %s đến %s.".formatted(e.startDate().format(DATE), e.endDate().format(DATE));
			notificationService.notify(e.userId(), NotificationType.PAYMENT, "Thanh toán thành công",
					"Đã thanh toán %sđ cho %s.%s".formatted(formatVnd(e.amount()), e.packageName(), validity),
					"PAYMENT", e.paymentId(), true);
		} else {
			notificationService.notify(e.userId(), NotificationType.PAYMENT, "Thanh toán không thành công",
					"Giao dịch %s không thành công. Bạn có thể đặt mua lại gói tập.".formatted(e.paymentCode()),
					"PAYMENT", e.paymentId(), false);
		}
	}

	// 1200000 -> "1.200.000"
	private static String formatVnd(BigDecimal amount) {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setGroupingSeparator('.');
		return new DecimalFormat("#,##0", symbols).format(amount);
	}
}
