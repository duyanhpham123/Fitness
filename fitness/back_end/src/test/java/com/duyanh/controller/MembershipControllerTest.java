package com.duyanh.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.duyanh.entity.enums.BookingStatus;
import com.duyanh.repository.BookingRepository;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Dữ liệu mẫu dùng trong test (02_seed.sql):
 *   gói id 1 = GYM_1M (30 ngày, 1.200.000đ), id 2 = GYM_3M (bảo lưu tối đa 15 ngày)
 *   member1: subscription id 1 (GYM_3M) đang ACTIVE
 *   member2: subscription id 2
 *   member3: không có gói còn hạn; payment id 4 là VNPAY đang PENDING
 */
class MembershipControllerTest extends ApiTestSupport {

	private static final String NEW_PACKAGE = """
			{"code":"GYM_2M","name":"Gói 2 tháng","durationDays":60,"price":2200000,"ptSessions":1,"maxFreezeDays":10}
			""";

	@Autowired
	BookingRepository bookingRepository;

	// Lịch đặt mẫu của member1 sẽ chặn việc bảo lưu; hủy trong transaction của test (tự rollback)
	@BeforeEach
	void cancelSeedBookingsOfMember1() {
		bookingRepository.findAll().stream()
				.filter(b -> b.getMember().getEmail().equals(MEMBER1))
				.forEach(b -> b.setStatus(BookingStatus.CANCELLED));
	}

	@Test
	void listPackages_isPublic_andHidesInactiveFromNonAdmin() throws Exception {
		mvc.perform(get("/api/packages"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(5))
				.andExpect(jsonPath("$[0].durationDays").value(30));

		mvc.perform(get("/api/packages").param("includeInactive", "true"))
				.andExpect(status().isForbidden());
		mvc.perform(auth(get("/api/packages"), ADMIN).param("includeInactive", "true"))
				.andExpect(status().isOk());
	}

	@Test
	void adminManagesPackages_othersForbidden() throws Exception {
		postAs(MEMBER1, "/api/packages", NEW_PACKAGE)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		postAs(ADMIN, "/api/packages", NEW_PACKAGE)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("GYM_2M"))
				.andExpect(jsonPath("$.active").value(true));
		postAs(ADMIN, "/api/packages", NEW_PACKAGE)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PACKAGE_CODE_EXISTS"));

		postAs(ADMIN, "/api/packages", """
				{"code":"bad code","name":"","durationDays":0,"price":-1,"ptSessions":0,"maxFreezeDays":0}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.code").exists())
				.andExpect(jsonPath("$.errors.durationDays").exists())
				.andExpect(jsonPath("$.errors.price").exists());

		// Ngừng bán gói 1 tháng -> biến mất khỏi danh sách công khai và không mua được
		putAs(ADMIN, "/api/packages/1", """
				{"code":"GYM_1M","name":"Gói 1 tháng","durationDays":30,"price":1200000,"ptSessions":0,"maxFreezeDays":0,"active":false}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
		mvc.perform(get("/api/packages")).andExpect(jsonPath("$.length()").value(5));
		purchase(MEMBER3, 1, "CASH", null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PACKAGE_NOT_AVAILABLE"));
	}

	@Test
	void purchase_withCash_thenStaffConfirms_activatesFromToday() throws Exception {
		JsonNode created = readJson(purchase(MEMBER3, 1, "CASH", "key-001")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.subscription.status").value("PENDING"))
				.andExpect(jsonPath("$.subscription.startDate").isEmpty())
				.andExpect(jsonPath("$.payment.status").value("PENDING"))
				.andExpect(jsonPath("$.payment.amount").value(1200000)));
		long paymentId = created.at("/payment/id").asLong();

		// Bấm mua 2 lần với cùng Idempotency-Key -> cùng 1 đơn
		purchase(MEMBER3, 1, "CASH", "key-001").andExpect(jsonPath("$.payment.id").value(paymentId));
		purchase(MEMBER3, 2, "CASH", "key-001")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

		postAs(MEMBER3, "/api/payments/" + paymentId + "/confirm", "").andExpect(status().isForbidden());

		LocalDate today = LocalDate.now();
		postAs(STAFF, "/api/payments/" + paymentId + "/confirm", "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payment.status").value("SUCCESS"))
				.andExpect(jsonPath("$.subscription.status").value("ACTIVE"))
				.andExpect(jsonPath("$.subscription.startDate").value(today.toString()))
				.andExpect(jsonPath("$.subscription.endDate").value(today.plusDays(29).toString()));

		postAs(STAFF, "/api/payments/" + paymentId + "/confirm", "")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PAYMENT_NOT_PENDING"));
	}

	@Test
	void confirm_onlineGatewayPayment_isRejected() throws Exception {
		postAs(STAFF, "/api/payments/4/confirm", "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PAYMENT_METHOD_NOT_MANUAL"));
	}

	@Test
	void purchase_requiresMemberRole() throws Exception {
		purchase(STAFF, 1, "CASH", null).andExpect(status().isForbidden());
	}

	@Test
	void renewal_startsAfterCurrent_andFreezeShiftsQueuedSubscription() throws Exception {
		LocalDate currentEnd = LocalDate.parse(findSubscription(MEMBER1, 1).get("endDate").asText());

		// Gia hạn khi gói hiện tại còn hạn -> gói mới bắt đầu ngay sau ngày kết thúc
		long paymentId = readJson(purchase(MEMBER1, 1, "CASH", null)).at("/payment/id").asLong();
		JsonNode renewed = readJson(postAs(STAFF, "/api/payments/" + paymentId + "/confirm", "")
				.andExpect(status().isOk())).get("subscription");
		long renewedId = renewed.get("id").asLong();
		assertEquals(currentEnd.plusDays(1).toString(), renewed.get("startDate").asText());
		assertEquals(currentEnd.plusDays(30).toString(), renewed.get("endDate").asText());

		// Bảo lưu gói hiện tại 5 ngày -> gói hiện tại và gói nối tiếp đều lùi 5 ngày
		postAs(MEMBER1, "/api/subscriptions/1/freeze", "{\"days\":5}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FROZEN"))
				.andExpect(jsonPath("$.endDate").value(currentEnd.plusDays(5).toString()))
				.andExpect(jsonPath("$.remainingFreezeDays").value(10));
		assertEquals(currentEnd.plusDays(6).toString(), findSubscription(MEMBER1, renewedId).get("startDate").asText());

		postAs(MEMBER1, "/api/subscriptions/1/freeze", "{\"days\":1}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FREEZABLE"));

		// Hủy bảo lưu ngay trong ngày -> hoàn lại đủ 5 ngày
		postAs(MEMBER1, "/api/subscriptions/1/unfreeze", "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.endDate").value(currentEnd.toString()))
				.andExpect(jsonPath("$.frozenDaysUsed").value(0))
				.andExpect(jsonPath("$.frozenFrom").isEmpty());
		assertEquals(currentEnd.plusDays(1).toString(), findSubscription(MEMBER1, renewedId).get("startDate").asText());
	}

	@Test
	void freeze_overLimit_orNotOwner_isRejected() throws Exception {
		postAs(MEMBER1, "/api/subscriptions/1/freeze", "{\"days\":16}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("FREEZE_LIMIT_EXCEEDED"));

		// Gói id 2 thuộc member2
		postAs(MEMBER1, "/api/subscriptions/2/freeze", "{\"days\":1}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));
	}

	private org.springframework.test.web.servlet.ResultActions purchase(String email, long packageId, String method,
			String idempotencyKey) throws Exception {
		var req = auth(post("/api/subscriptions"), email)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"packageId\":%d,\"method\":\"%s\"}".formatted(packageId, method));
		if (idempotencyKey != null) {
			req.header("Idempotency-Key", idempotencyKey);
		}
		return mvc.perform(req);
	}

	private JsonNode findSubscription(String email, long id) throws Exception {
		for (JsonNode s : readJson(getAs(email, "/api/subscriptions/me").andExpect(status().isOk()))) {
			if (s.get("id").asLong() == id) {
				return s;
			}
		}
		throw new AssertionError("Không thấy subscription id=" + id);
	}
}
