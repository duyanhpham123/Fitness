package com.duyanh.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.duyanh.config.OtpProperties;
import com.duyanh.dto.otp.OtpSentResponse;
import com.duyanh.entity.enums.OtpPurpose;
import com.duyanh.exception.ApiException;
import com.duyanh.security.HashUtils;

import lombok.RequiredArgsConstructor;

/**
 * Tạo và kiểm tra mã OTP 6 số, lưu trên Redis (không lưu MySQL).
 *
 * Key Redis (email đã chuẩn hóa chữ thường):
 *   otp:{purpose}:{email}           hash { code = sha256(mã), attempts = số lần nhập sai }, TTL 5 phút
 *   otp:cooldown:{purpose}:{email}  chặn gửi lại trong 60 giây
 *   otp:count:{purpose}:{email}     số lần gửi trong 1 giờ
 */
@Service
@RequiredArgsConstructor
public class OtpService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String FIELD_CODE = "code";
	private static final String FIELD_ATTEMPTS = "attempts";

	private final StringRedisTemplate redis;
	private final OtpProperties props;

	/**
	 * Kiểm tra giới hạn gửi. Gọi trước khi quyết định có gửi thật hay không, để mọi email
	 * (kể cả email không tồn tại) đều bị giới hạn giống nhau.
	 *
	 * @throws ApiException 429 nếu gửi quá nhanh hoặc quá nhiều
	 */
	public void checkSendLimit(OtpPurpose purpose, String email) {
		String cooldownKey = key("otp:cooldown", purpose, email);
		Boolean acquired = redis.opsForValue()
				.setIfAbsent(cooldownKey, "1", Duration.ofSeconds(props.resendCooldownSeconds()));
		if (!Boolean.TRUE.equals(acquired)) {
			Long ttl = redis.getExpire(cooldownKey);
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN",
					"Vui lòng đợi %d giây trước khi gửi lại mã".formatted(ttl == null || ttl < 1 ? 1 : ttl));
		}

		String countKey = key("otp:count", purpose, email);
		Long count = redis.opsForValue().increment(countKey);
		if (count != null && count == 1) {
			redis.expire(countKey, Duration.ofHours(1));
		}
		if (count != null && count > props.maxSendsPerHour()) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_TOO_MANY_REQUESTS",
					"Bạn đã yêu cầu quá nhiều mã, vui lòng thử lại sau 1 giờ");
		}
	}

	/**
	 * Tạo mã mới (mã cũ nếu còn sẽ bị thay thế). Trả về mã gốc để gửi email.
	 */
	public String generate(OtpPurpose purpose, String email) {
		String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
		String otpKey = key("otp", purpose, email);
		redis.delete(otpKey);
		redis.opsForHash().putAll(otpKey, Map.of(FIELD_CODE, HashUtils.sha256Hex(code), FIELD_ATTEMPTS, "0"));
		redis.expire(otpKey, Duration.ofSeconds(props.expirationSeconds()));
		return code;
	}

	/**
	 * Kiểm tra và "tiêu thụ" mã: đúng thì xóa ngay, mỗi mã chỉ dùng được 1 lần.
	 *
	 * @throws ApiException 400 OTP_INVALID / OTP_EXPIRED, 429 OTP_TOO_MANY_ATTEMPTS
	 */
	public void verify(OtpPurpose purpose, String email, String code) {
		String otpKey = key("otp", purpose, email);
		List<Object> values = redis.opsForHash().multiGet(otpKey, List.of(FIELD_CODE, FIELD_ATTEMPTS));
		Object storedHash = values.get(0);
		if (storedHash == null) {
			throw ApiException.badRequest("OTP_EXPIRED", "Mã OTP đã hết hạn hoặc không tồn tại, vui lòng gửi lại mã");
		}

		int attempts = Integer.parseInt(String.valueOf(values.get(1)));
		if (attempts >= props.maxAttempts()) {
			redis.delete(otpKey);
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_TOO_MANY_ATTEMPTS",
					"Nhập sai quá nhiều lần, vui lòng gửi lại mã mới");
		}

		if (!HashUtils.constantTimeEquals(storedHash.toString(), HashUtils.sha256Hex(code))) {
			long used = redis.opsForHash().increment(otpKey, FIELD_ATTEMPTS, 1);
			long remaining = props.maxAttempts() - used;
			if (remaining <= 0) {
				redis.delete(otpKey);
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_TOO_MANY_ATTEMPTS",
						"Nhập sai quá nhiều lần, vui lòng gửi lại mã mới");
			}
			throw ApiException.badRequest("OTP_INVALID", "Mã OTP không đúng, còn %d lần thử".formatted(remaining));
		}

		// delete trả về false nghĩa là một request khác vừa dùng mã này trước
		if (!Boolean.TRUE.equals(redis.delete(otpKey))) {
			throw ApiException.badRequest("OTP_EXPIRED", "Mã OTP đã được sử dụng");
		}
	}

	public OtpSentResponse sentResponse(String code) {
		return new OtpSentResponse(
				"Nếu thông tin hợp lệ, mã OTP đã được gửi tới email của bạn",
				props.expirationSeconds(),
				props.resendCooldownSeconds(),
				props.exposeInResponse() ? code : null);
	}

	private static String key(String prefix, OtpPurpose purpose, String email) {
		return prefix + ":" + purpose.name() + ":" + email;
	}
}
