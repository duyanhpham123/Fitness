package com.duyanh.scheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.duyanh.config.VnpayProperties;
import com.duyanh.service.PaymentService;

import lombok.RequiredArgsConstructor;

/**
 * 5 phút một lần: hủy đơn thanh toán online bị bỏ dở.
 * Chờ thêm 15 phút sau khi link VNPay hết hạn để IPN đến trễ vẫn kịp xử lý.
 */
@Component
@RequiredArgsConstructor
public class PaymentScheduler {

	private static final int GRACE_MINUTES = 15;

	private final PaymentService paymentService;
	private final VnpayProperties vnpayProperties;

	@Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
	public void cancelExpiredOnlinePayments() {
		paymentService.cancelExpiredOnline(
				LocalDateTime.now().minusMinutes(vnpayProperties.expireMinutes() + GRACE_MINUTES));
	}
}
