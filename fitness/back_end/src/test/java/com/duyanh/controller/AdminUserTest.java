package com.duyanh.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Dữ liệu mẫu: admin id 1, member1 id 5 (gói 3 tháng ACTIVE), member2 id 6, member3 id 7 (gói 1 tháng EXPIRED),
 * 2 HLV id 3, 4.
 */
class AdminUserTest extends ApiTestSupport {

	private static final long ADMIN_ID = 1;
	private static final long MEMBER1_ID = 5;
	private static final long MEMBER2_ID = 6;
	private static final long MEMBER3_ID = 7;

	@Autowired
	StringRedisTemplate redis;

	// Khóa tài khoản ghi key thu hồi token lên Redis (không rollback theo DB), xóa để không ảnh hưởng test khác
	@AfterEach
	void clearRevokedTokens() {
		redis.delete("auth:revoked-before:" + MEMBER1_ID);
		redis.delete("auth:revoked-before:" + MEMBER2_ID);
	}

	@Test
	void searchUsers_byRoleAndKeyword_adminOnly() throws Exception {
		getAs(ADMIN, "/api/admin/users?role=TRAINER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));
		getAs(ADMIN, "/api/admin/users?keyword=member")
				.andExpect(jsonPath("$.totalElements").value(3));
		getAs(ADMIN, "/api/admin/users?status=LOCKED")
				.andExpect(jsonPath("$.totalElements").value(0));
		getAs(STAFF, "/api/admin/users").andExpect(status().isForbidden());
	}

	@Test
	void createTrainer_appearsInTrainerList_andCanLogin() throws Exception {
		String body = """
				{"email":"New.Trainer@fitness.local","password":"%s","fullName":"HLV Mới","role":"TRAINER","specialization":"Boxing"}
				""".formatted(SEED_PASSWORD);
		postAs(ADMIN, "/api/admin/users", body)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("new.trainer@fitness.local"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.roles[0]").value("TRAINER"));
		postAs(ADMIN, "/api/admin/users", body)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));

		mvc.perform(get("/api/trainers"))
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[?(@.fullName == 'HLV Mới')].specialization").value("Boxing"));
		login("new.trainer@fitness.local").andExpect(status().isOk());
	}

	@Test
	void lockUser_revokesSessions_andBlocksLogin_untilUnlocked() throws Exception {
		String member1Token = tokenOf(MEMBER1);

		putAs(ADMIN, "/api/admin/users/" + MEMBER1_ID + "/status", "{\"status\":\"LOCKED\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("LOCKED"));

		mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + member1Token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
		login(MEMBER1)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

		putAs(ADMIN, "/api/admin/users/" + MEMBER1_ID + "/status", "{\"status\":\"ACTIVE\"}")
				.andExpect(status().isOk());
		// iat của JWT tính theo giây: sang giây mới để token mới chắc chắn cấp sau thời điểm thu hồi
		Thread.sleep(1100);
		String fresh = readJson(login(MEMBER1).andExpect(status().isOk())).get("accessToken").asText();
		mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + fresh))
				.andExpect(status().isOk());
	}

	@Test
	void adminCannotLockSelf_orDropOwnAdminRole() throws Exception {
		putAs(ADMIN, "/api/admin/users/" + ADMIN_ID + "/status", "{\"status\":\"LOCKED\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
		putAs(ADMIN, "/api/admin/users/" + ADMIN_ID + "/roles", "{\"roles\":[\"STAFF\"]}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
		putAs(ADMIN, "/api/admin/users/" + MEMBER1_ID + "/status", "{\"status\":\"PENDING\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS"));
	}

	@Test
	void changeRoles_togglesTrainerProfile_andIsAudited() throws Exception {
		putAs(ADMIN, "/api/admin/users/" + MEMBER2_ID + "/roles", "{\"roles\":[\"MEMBER\",\"TRAINER\"]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles.length()").value(2));
		mvc.perform(get("/api/trainers")).andExpect(jsonPath("$.length()").value(3));

		putAs(ADMIN, "/api/admin/users/" + MEMBER2_ID + "/roles", "{\"roles\":[\"MEMBER\"]}")
				.andExpect(status().isOk());
		mvc.perform(get("/api/trainers")).andExpect(jsonPath("$.length()").value(2));

		getAs(ADMIN, "/api/audit-logs?action=USER_ROLES_CHANGE&entityId=" + MEMBER2_ID)
				.andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	void listMembers_showsLatestSubscription() throws Exception {
		getAs(STAFF, "/api/admin/members?keyword=member1")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].packageName").value("Gói 3 tháng"))
				.andExpect(jsonPath("$.content[0].subscriptionStatus").value("ACTIVE"));
		getAs(ADMIN, "/api/admin/members?keyword=member3")
				.andExpect(jsonPath("$.content[0].packageName").value("Gói 1 tháng"))
				.andExpect(jsonPath("$.content[0].subscriptionStatus").value("EXPIRED"));
		getAs(MEMBER1, "/api/admin/members").andExpect(status().isForbidden());
	}

	@Test
	void updateUser_rejectsDuplicatePhone() throws Exception {
		// 0987654321 là SĐT của member1
		putAs(ADMIN, "/api/admin/users/" + MEMBER3_ID, "{\"fullName\":\"Lê Hoàng Anh\",\"phone\":\"0987654321\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PHONE_EXISTS"));
		putAs(ADMIN, "/api/admin/users/" + MEMBER3_ID,
				"{\"fullName\":\"Lê Hoàng Anh (VIP)\",\"phone\":\"0978123456\",\"gender\":\"MALE\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Lê Hoàng Anh (VIP)"));
	}

	private ResultActions login(String email) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, SEED_PASSWORD)));
	}
}
