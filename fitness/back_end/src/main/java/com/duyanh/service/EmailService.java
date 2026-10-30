package com.duyanh.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.duyanh.entity.enums.OtpPurpose;

import lombok.extern.slf4j.Slf4j;

/**
 * Gửi email bất đồng bộ (@Async) để request không phải chờ SMTP.
 *
 * JavaMailSender chỉ tồn tại khi đã cấu hình spring.mail.host. Chưa cấu hình thì chỉ ghi log,
 * dev lấy mã qua field devOtp trong response.
 */
@Slf4j
@Service
public class EmailService {

	private final ObjectProvider<JavaMailSender> mailSender;
	private final String from;

	public EmailService(ObjectProvider<JavaMailSender> mailSender,
			@Value("${app.mail.from:no-reply@fitness.local}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Async
	public void sendOtp(String to, String code, OtpPurpose purpose, long expiresInSeconds) {
		doSend(to, "[Fitness] Mã OTP " + purpose.getLabel().toLowerCase(), """
				Mã OTP của bạn là: %s

				Mã có hiệu lực trong %d phút. Không chia sẻ mã này cho bất kỳ ai.
				Nếu bạn không yêu cầu, hãy bỏ qua email này.
				""".formatted(code, expiresInSeconds / 60));
	}

	@Async
	public void send(String to, String subject, String body) {
		doSend(to, subject, body);
	}

	private void doSend(String to, String subject, String body) {
		JavaMailSender sender = mailSender.getIfAvailable();
		if (sender == null) {
			log.info("Mail chưa cấu hình (spring.mail.host), bỏ qua email '{}' tới {}", subject, to);
			return;
		}
		SimpleMailMessage msg = new SimpleMailMessage();
		msg.setFrom(from);
		msg.setTo(to);
		msg.setSubject(subject);
		msg.setText(body);
		try {
			sender.send(msg);
		} catch (Exception e) {
			log.error("Gửi email '{}' tới {} thất bại", subject, to, e);
		}
	}
}
