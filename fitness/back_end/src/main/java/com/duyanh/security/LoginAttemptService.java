package com.duyanh.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.duyanh.exception.ApiException;

/**
 * Chống dò mật khẩu: sai quá max-login-attempts lần thì khóa đăng nhập theo email trong login-lock-minutes phút.
 *
 * Redis: auth:login-fail:{email} = số lần sai, TTL tính từ lần sai đầu tiên.
 * Đếm theo email (không theo IP) nên đổi IP cũng không dò tiếp được.
 */
@Service
public class LoginAttemptService {

	private static final String KEY_PREFIX = "auth:login-fail:";

	private final StringRedisTemplate redis;
	private final int maxAttempts;
	private final Duration lockDuration;

	public LoginAttemptService(StringRedisTemplate redis,
			@Value("${app.auth.max-login-attempts:5}") int maxAttempts,
			@Value("${app.auth.login-lock-minutes:15}") int lockMinutes) {
		this.redis = redis;
		this.maxAttempts = maxAttempts;
		this.lockDuration = Duration.ofMinutes(lockMinutes);
	}

	/**
	 * @throws ApiException 429 nếu email đang bị khóa đăng nhập
	 */
	public void checkNotLocked(String email) {
		String count = redis.opsForValue().get(KEY_PREFIX + email);
		if (count != null && Integer.parseInt(count) >= maxAttempts) {
			Long ttl = redis.getExpire(KEY_PREFIX + email);
			long minutes = ttl == null || ttl <= 0 ? 1 : (ttl + 59) / 60;
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_LOGIN_ATTEMPTS",
					"Đăng nhập sai quá nhiều lần, vui lòng thử lại sau %d phút".formatted(minutes));
		}
	}

	public void recordFailure(String email) {
		String key = KEY_PREFIX + email;
		Long count = redis.opsForValue().increment(key);
		if (count != null && count == 1) {
			redis.expire(key, lockDuration);
		}
	}

	public void reset(String email) {
		redis.delete(KEY_PREFIX + email);
	}
}
