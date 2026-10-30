package com.duyanh.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.duyanh.security.JwtService.ParsedToken;

import lombok.RequiredArgsConstructor;

/**
 * Vô hiệu hóa access token trước khi hết hạn, lưu trên Redis.
 *
 *   auth:blacklist:{jti}            1 token cụ thể (logout), TTL = thời gian còn lại của token
 *   auth:revoked-before:{userId}    mọi token của user cấp trước thời điểm này (đổi mật khẩu,
 *                                   khóa tài khoản), TTL = thời hạn access token
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

	private final StringRedisTemplate redis;
	private final JwtProperties jwtProperties;

	public void blacklist(ParsedToken token) {
		Duration ttl = Duration.between(Instant.now(), token.expiresAt());
		if (!ttl.isNegative() && !ttl.isZero()) {
			redis.opsForValue().set("auth:blacklist:" + token.jti(), "1", ttl);
		}
	}

	public void revokeAllForUser(Long userId) {
		redis.opsForValue().set("auth:revoked-before:" + userId,
				String.valueOf(Instant.now().getEpochSecond()),
				Duration.ofMillis(jwtProperties.accessExpirationMs()));
	}

	public boolean isRevoked(ParsedToken token) {
		if (Boolean.TRUE.equals(redis.hasKey("auth:blacklist:" + token.jti()))) {
			return true;
		}
		String revokedBefore = redis.opsForValue().get("auth:revoked-before:" + token.principal().getId());
		// iat trong JWT tính theo giây, nên token cấp cùng giây với lúc thu hồi cũng bị chặn
		return revokedBefore != null && token.issuedAt().getEpochSecond() <= Long.parseLong(revokedBefore);
	}
}
