package com.duyanh.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Tạo và kiểm tra access token (JWT ký HMAC).
 * Refresh token KHÔNG phải JWT: là chuỗi ngẫu nhiên, lưu hash trong bảng refresh_tokens.
 */
@Service
public class JwtService {

	private static final String CLAIM_EMAIL = "email";
	private static final String CLAIM_ROLES = "roles";

	private final JwtProperties props;
	private final SecretKey key;

	public JwtService(JwtProperties props) {
		this.props = props;
		// Ném WeakKeyException ngay lúc khởi động nếu secret ngắn hơn 32 byte
		this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
	}

	public String generateAccessToken(UserPrincipal principal) {
		Date now = new Date();
		return Jwts.builder()
				// jti: mã riêng của token, dùng để đưa vào blacklist khi logout
				.id(UUID.randomUUID().toString())
				.subject(principal.getId().toString())
				.claim(CLAIM_EMAIL, principal.getEmail())
				.claim(CLAIM_ROLES, principal.getRoles())
				.issuedAt(now)
				.expiration(new Date(now.getTime() + props.accessExpirationMs()))
				.signWith(key)
				.compact();
	}

	/**
	 * @throws io.jsonwebtoken.ExpiredJwtException token hết hạn
	 * @throws JwtException token sai chữ ký / sai định dạng
	 */
	public ParsedToken parseAccessToken(String token) {
		Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		List<?> roles = claims.get(CLAIM_ROLES, List.class);
		if (roles == null || claims.getId() == null || claims.getIssuedAt() == null) {
			throw new JwtException("Missing required claims");
		}
		UserPrincipal principal = UserPrincipal.fromToken(
				Long.valueOf(claims.getSubject()),
				claims.get(CLAIM_EMAIL, String.class),
				roles.stream().map(Object::toString).toList());
		return new ParsedToken(principal, claims.getId(), claims.getIssuedAt().toInstant(),
				claims.getExpiration().toInstant());
	}

	public long getAccessExpirationSeconds() {
		return props.accessExpirationMs() / 1000;
	}

	public record ParsedToken(UserPrincipal principal, String jti, Instant issuedAt, Instant expiresAt) {
	}
}
