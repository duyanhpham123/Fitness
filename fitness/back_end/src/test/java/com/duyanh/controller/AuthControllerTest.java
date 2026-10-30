package com.duyanh.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Cần MySQL (fitness_db + 02_seed.sql) và Redis đang chạy.
 * Dữ liệu MySQL tự rollback sau mỗi test; key Redis của các email dùng trong test được xóa trước và sau mỗi test.
 * Tài khoản mẫu: member1@fitness.local / Fitness@123
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

	private static final String SEED_EMAIL = "member1@fitness.local";
	private static final String SEED_PASSWORD = "Fitness@123";
	private static final String NEW_EMAIL = "new.member@fitness.local";
	private static final List<String> TEST_EMAILS = List.of(SEED_EMAIL, NEW_EMAIL, "nobody@fitness.local");

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	UserRepository userRepository;

	@BeforeEach
	@AfterEach
	void clearRedis() {
		for (String email : TEST_EMAILS) {
			var keys = redis.keys("otp*:" + email);
			if (keys != null && !keys.isEmpty()) {
				redis.delete(keys);
			}
		}
		userRepository.findByEmail(SEED_EMAIL).ifPresent(u -> redis.delete("auth:revoked-before:" + u.getId()));
	}

	@Test
	void register_verifyEmail_thenGetProfile() throws Exception {
		JsonNode registered = readJson(postJson("/api/auth/register", """
				{"email":"New.Member@Fitness.local","password":"abc12345","fullName":"Hội viên mới","phone":"0911222333"}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user.email").value(NEW_EMAIL))
				.andExpect(jsonPath("$.user.status").value("PENDING"))
				.andExpect(jsonPath("$.user.roles", hasItem("MEMBER")))
				.andExpect(jsonPath("$.user.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.otp.devOtp").isNotEmpty()));
		String otp = registered.at("/otp/devOtp").asText();

		// Chưa xác thực email -> không đăng nhập được
		postJson("/api/auth/login", loginBody(NEW_EMAIL, "abc12345"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_VERIFIED"));

		// Sai mã -> báo số lần còn lại
		postJson("/api/auth/verify-email", verifyBody(NEW_EMAIL, wrongCode(otp)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("OTP_INVALID"))
				.andExpect(jsonPath("$.message").value(startsWith("Mã OTP không đúng, còn 4")));

		// Đúng mã -> kích hoạt + trả token
		JsonNode tokens = readJson(postJson("/api/auth/verify-email", verifyBody(NEW_EMAIL, otp))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.status").value("ACTIVE")));

		// Mã chỉ dùng được 1 lần
		postJson("/api/auth/verify-email", verifyBody(NEW_EMAIL, otp))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("OTP_EXPIRED"));

		mvc.perform(get("/api/users/me").header("Authorization", bearer(tokens)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Hội viên mới"));
	}

	@Test
	void verifyEmail_tooManyWrongAttempts_invalidatesCode() throws Exception {
		String otp = readJson(postJson("/api/auth/register", """
				{"email":"new.member@fitness.local","password":"abc12345","fullName":"Hội viên mới"}
				""").andExpect(status().isCreated())).at("/otp/devOtp").asText();

		for (int i = 0; i < 4; i++) {
			postJson("/api/auth/verify-email", verifyBody(NEW_EMAIL, wrongCode(otp)))
					.andExpect(jsonPath("$.code").value("OTP_INVALID"));
		}
		postJson("/api/auth/verify-email", verifyBody(NEW_EMAIL, wrongCode(otp)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("OTP_TOO_MANY_ATTEMPTS"));

		// Mã đúng cũng không dùng được nữa
		postJson("/api/auth/verify-email", verifyBody(NEW_EMAIL, otp))
				.andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
	}

	@Test
	void sendOtp_resendTooSoon_returns429() throws Exception {
		postJson("/api/otp/send", """
				{"email":"member1@fitness.local","purpose":"RESET_PASSWORD"}
				""").andExpect(status().isOk());

		postJson("/api/otp/send", """
				{"email":"member1@fitness.local","purpose":"RESET_PASSWORD"}
				""")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("OTP_COOLDOWN"));
	}

	@Test
	void forgotPassword_unknownEmail_returnsSameMessage_withoutCode() throws Exception {
		postJson("/api/auth/forgot-password", "{\"email\":\"nobody@fitness.local\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.devOtp").doesNotExist());
	}

	@Test
	void resetPassword_revokesAllSessions_andAcceptsNewPassword() throws Exception {
		JsonNode oldSession = login(SEED_EMAIL, SEED_PASSWORD);

		String otp = readJson(postJson("/api/auth/forgot-password", "{\"email\":\"" + SEED_EMAIL + "\"}")
				.andExpect(status().isOk())).get("devOtp").asText();

		// iat của JWT tính theo giây: chờ sang giây mới để token cũ chắc chắn được cấp trước lúc reset
		Thread.sleep(1100);
		postJson("/api/auth/reset-password", """
				{"email":"%s","otp":"%s","newPassword":"NewPass123"}
				""".formatted(SEED_EMAIL, otp))
				.andExpect(status().isNoContent());

		// Access token và refresh token cũ đều bị thu hồi
		mvc.perform(get("/api/users/me").header("Authorization", bearer(oldSession)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
		refresh(oldSession.get("refreshToken").asText()).andExpect(status().isUnauthorized());

		postJson("/api/auth/login", loginBody(SEED_EMAIL, SEED_PASSWORD)).andExpect(status().isUnauthorized());
		Thread.sleep(1100);
		login(SEED_EMAIL, "NewPass123");
	}

	@Test
	void register_duplicateEmail_returns409() throws Exception {
		postJson("/api/auth/register", """
				{"email":"member1@fitness.local","password":"abc12345","fullName":"Trùng email"}
				""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
	}

	@Test
	void register_invalidBody_returnsFieldErrors() throws Exception {
		postJson("/api/auth/register", """
				{"email":"not-an-email","password":"short","fullName":"","phone":"123"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors.email").exists())
				.andExpect(jsonPath("$.errors.password").exists())
				.andExpect(jsonPath("$.errors.fullName").exists())
				.andExpect(jsonPath("$.errors.phone").exists());
	}

	@Test
	void login_wrongPassword_returns401() throws Exception {
		postJson("/api/auth/login", loginBody(SEED_EMAIL, "wrong-password"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
	}

	@Test
	void protectedEndpoint_withoutOrBadToken_returns401() throws Exception {
		mvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mvc.perform(get("/api/users/me").header("Authorization", "Bearer abc.def.ghi"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void refresh_rotatesToken_andReuseRevokesAllSessions() throws Exception {
		String first = login(SEED_EMAIL, SEED_PASSWORD).get("refreshToken").asText();

		// Refresh lần 1: nhận refresh token mới
		String second = readJson(refresh(first).andExpect(status().isOk())).get("refreshToken").asText();

		// Dùng lại token cũ -> bị coi là đánh cắp -> 401 và thu hồi luôn token mới
		refresh(first)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
		refresh(second).andExpect(status().isUnauthorized());
	}

	@Test
	void logout_revokesRefreshToken_andBlacklistsAccessToken() throws Exception {
		JsonNode session = login(SEED_EMAIL, SEED_PASSWORD);
		String refreshToken = session.get("refreshToken").asText();

		mvc.perform(post("/api/auth/logout").header("Authorization", bearer(session))
				.contentType(MediaType.APPLICATION_JSON).content(refreshBody(refreshToken)))
				.andExpect(status().isNoContent());

		refresh(refreshToken).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/users/me").header("Authorization", bearer(session)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));

		// Đăng xuất lần 2 vẫn trả 204
		postJson("/api/auth/logout", refreshBody(refreshToken)).andExpect(status().isNoContent());
	}

	private JsonNode login(String email, String password) throws Exception {
		return readJson(postJson("/api/auth/login", loginBody(email, password))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty()));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return postJson("/api/auth/refresh", refreshBody(refreshToken));
	}

	private static String loginBody(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
	}

	private static String verifyBody(String email, String otp) {
		return "{\"email\":\"%s\",\"otp\":\"%s\"}".formatted(email, otp);
	}

	private static String refreshBody(String refreshToken) {
		return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
	}

	private static String bearer(JsonNode tokens) {
		return "Bearer " + tokens.get("accessToken").asText();
	}

	// Mã khác với mã đúng nhưng vẫn đúng định dạng 6 số
	private static String wrongCode(String otp) {
		return otp.equals("000000") ? "111111" : "000000";
	}

	private ResultActions postJson(String url, String json) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private JsonNode readJson(ResultActions result) throws Exception {
		return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
	}
}
