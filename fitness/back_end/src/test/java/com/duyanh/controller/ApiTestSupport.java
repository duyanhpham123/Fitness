package com.duyanh.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.TestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lớp cha cho test API: đăng nhập bằng tài khoản mẫu và gọi API kèm token.
 * Chạy trên MySQL + Redis tạm của Testcontainers (cần Docker), dữ liệu mẫu do Flyway nạp.
 * Dữ liệu MySQL tự rollback sau mỗi test.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@AutoConfigureMockMvc
@Transactional
abstract class ApiTestSupport {

	// Mật khẩu chung của tài khoản mẫu, xem 02_seed.sql
	protected static final String SEED_PASSWORD = "Fitness@123";
	protected static final String ADMIN = "admin@fitness.local";
	protected static final String STAFF = "staff@fitness.local";
	protected static final String MEMBER1 = "member1@fitness.local";
	protected static final String MEMBER3 = "member3@fitness.local";

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected ObjectMapper objectMapper;

	private final Map<String, String> tokens = new HashMap<>();

	/** Access token của tài khoản mẫu (đăng nhập 1 lần mỗi test). */
	protected String tokenOf(String email) throws Exception {
		String cached = tokens.get(email);
		if (cached != null) {
			return cached;
		}
		String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, SEED_PASSWORD);
		JsonNode res = readJson(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()));
		String token = res.get("accessToken").asText();
		tokens.put(email, token);
		return token;
	}

	protected ResultActions getAs(String email, String url) throws Exception {
		return mvc.perform(auth(get(url), email));
	}

	protected ResultActions postAs(String email, String url, String json) throws Exception {
		return mvc.perform(auth(post(url), email).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected ResultActions putAs(String email, String url, String json) throws Exception {
		return mvc.perform(auth(put(url), email).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, String email) throws Exception {
		return email == null ? req : req.header("Authorization", "Bearer " + tokenOf(email));
	}

	protected JsonNode readJson(ResultActions result) throws Exception {
		return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
	}
}
