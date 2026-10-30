package com.duyanh.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Hồ sơ cá nhân, đổi mật khẩu, khóa tạm khi đăng nhập sai nhiều lần.
 * member1 = id 5, SĐT của member2 là 0912345678.
 */
class AccountSecurityTest extends ApiTestSupport {

	private static final long MEMBER1_ID = 5;

	@Autowired
	StringRedisTemplate redis;

	// Key Redis không rollback theo DB: dọn trước và sau mỗi test
	@BeforeEach
	@AfterEach
	void clearRedis() {
		redis.delete("auth:login-fail:" + MEMBER1);
		redis.delete("auth:login-fail:" + MEMBER3);
		redis.delete("auth:revoked-before:" + MEMBER1_ID);
	}

	@Test
	void updateProfile_rejectsDuplicatePhone() throws Exception {
		putAs(MEMBER1, "/api/users/me", """
				{"fullName":"Phạm Duy Anh","phone":"0912345678"}
				""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PHONE_EXISTS"));

		putAs(MEMBER1, "/api/users/me", """
				{"fullName":"Phạm Duy Anh (mới)","phone":"0987654321","gender":"MALE","address":"Hà Nội"}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Phạm Duy Anh (mới)"))
				.andExpect(jsonPath("$.address").value("Hà Nội"));
	}

	@Test
	void changePassword_revokesSessions_andRequiresNewPassword() throws Exception {
		String oldToken = tokenOf(MEMBER1);

		putAs(MEMBER1, "/api/users/me/password", "{\"currentPassword\":\"sai-mat-khau\",\"newPassword\":\"MatKhauMoi123\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
		putAs(MEMBER1, "/api/users/me/password", body(SEED_PASSWORD, SEED_PASSWORD))
				.andExpect(jsonPath("$.code").value("SAME_PASSWORD"));

		putAs(MEMBER1, "/api/users/me/password", body(SEED_PASSWORD, "MatKhauMoi123"))
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + oldToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
		login(MEMBER1, SEED_PASSWORD).andExpect(status().isUnauthorized());
		login(MEMBER1, "MatKhauMoi123").andExpect(status().isOk());
	}

	@Test
	void tooManyWrongPasswords_locksLoginTemporarily() throws Exception {
		for (int i = 0; i < 5; i++) {
			login(MEMBER3, "sai-mat-khau")
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
		}
		// Đúng mật khẩu cũng bị chặn trong thời gian khóa
		login(MEMBER3, SEED_PASSWORD)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
	}

	@Test
	void successfulLogin_resetsFailureCounter() throws Exception {
		login(MEMBER1, "sai-mat-khau").andExpect(status().isUnauthorized());
		login(MEMBER1, "sai-mat-khau").andExpect(status().isUnauthorized());
		login(MEMBER1, SEED_PASSWORD).andExpect(status().isOk());
		assertFalse(Boolean.TRUE.equals(redis.hasKey("auth:login-fail:" + MEMBER1)));
	}

	private static String body(String current, String next) {
		return "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next);
	}

	private ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}
}
