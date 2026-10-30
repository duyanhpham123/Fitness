package com.duyanh.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import com.duyanh.security.RefreshTokenCookie;

import jakarta.servlet.http.Cookie;

/**
 * Chế độ production: refresh token chỉ nằm trong cookie HttpOnly + Secure, không có trong body JSON.
 */
@TestPropertySource(properties = {
		"app.auth.refresh-token-in-body=false",
		"app.auth.refresh-cookie-secure=true"
})
class AuthCookieTest extends ApiTestSupport {

	private static final long MEMBER1_ID = 5;

	@Autowired
	StringRedisTemplate redis;

	// Dùng lại cookie cũ -> backend thu hồi mọi phiên của member1 bằng key Redis (không rollback theo DB);
	// xóa đi để token của member1 ở các test sau không bị chặn
	@AfterEach
	void clearRevokedTokens() {
		redis.delete("auth:revoked-before:" + MEMBER1_ID);
	}

	@Test
	void login_setsHttpOnlyCookie_andRefreshWorksWithCookieOnly() throws Exception {
		MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(MEMBER1, SEED_PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").doesNotExist())
				.andExpect(header().string("Set-Cookie", allOf(
						containsString(RefreshTokenCookie.NAME + "="),
						containsString("HttpOnly"),
						containsString("Secure"),
						containsString("SameSite=Lax"),
						containsString("Path=/api/auth"))))
				.andReturn();
		Cookie first = login.getResponse().getCookie(RefreshTokenCookie.NAME);

		// Refresh không cần body: token lấy từ cookie, cookie mới được cấp (token xoay vòng)
		MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(first))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.email").value(MEMBER1))
				.andReturn();
		Cookie second = refreshed.getResponse().getCookie(RefreshTokenCookie.NAME);

		// Cookie cũ đã bị thu hồi
		mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());

		// Đăng xuất bằng cookie -> cookie bị xóa (Max-Age=0), không refresh được nữa
		mvc.perform(post("/api/auth/logout").cookie(second))
				.andExpect(status().isNoContent())
				.andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
	}

	@Test
	void refresh_withoutCookieOrBody_returns401() throws Exception {
		mvc.perform(post("/api/auth/refresh"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
	}
}
