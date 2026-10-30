package com.duyanh.security;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.duyanh.dto.auth.AuthResponse;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Refresh token trong cookie HttpOnly: JavaScript (kể cả mã độc chèn qua XSS) không đọc được.
 *
 * Path=/api/auth: trình duyệt chỉ gửi cookie khi gọi login / refresh / logout, không gửi kèm mọi API.
 * SameSite=Lax: không gửi khi trang web khác gọi tới (chống CSRF cho refresh / logout).
 */
@Component
public class RefreshTokenCookie {

	public static final String NAME = "fitness_rt";
	private static final String PATH = "/api/auth";

	private final boolean secure;
	private final boolean tokenInBody;

	public RefreshTokenCookie(@Value("${app.auth.refresh-cookie-secure:false}") boolean secure,
			@Value("${app.auth.refresh-token-in-body:true}") boolean tokenInBody) {
		this.secure = secure;
		this.tokenInBody = tokenInBody;
	}

	/**
	 * Gắn cookie và trả về body. Production (refresh-token-in-body=false) bỏ refresh token khỏi body JSON.
	 */
	public AuthResponse write(HttpServletResponse response, AuthResponse auth) {
		long maxAge = Math.max(0, Duration.between(LocalDateTime.now(), auth.refreshTokenExpiresAt()).toSeconds());
		response.addHeader(HttpHeaders.SET_COOKIE, build(auth.refreshToken(), maxAge).toString());
		return tokenInBody ? auth : auth.withoutRefreshToken();
	}

	public void clear(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, build("", 0).toString());
	}

	/** Lấy refresh token: ưu tiên body (app di động / client cũ), không có thì đọc cookie */
	public String resolve(HttpServletRequest request, String fromBody) {
		if (fromBody != null && !fromBody.isBlank()) {
			return fromBody;
		}
		if (request.getCookies() != null) {
			for (Cookie c : request.getCookies()) {
				if (NAME.equals(c.getName()) && !c.getValue().isBlank()) {
					return c.getValue();
				}
			}
		}
		return null;
	}

	private ResponseCookie build(String value, long maxAgeSeconds) {
		return ResponseCookie.from(NAME, value)
				.httpOnly(true)
				.secure(secure)
				.sameSite("Lax")
				.path(PATH)
				.maxAge(maxAgeSeconds)
				.build();
	}
}
