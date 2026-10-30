package com.duyanh.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.duyanh.repository.SubscriptionRepository;
import com.duyanh.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Dữ liệu mẫu dùng trong test (02_seed.sql):
 *   member1 (id 5): 1 thông báo chưa đọc, gói id 1 đang ACTIVE; member3 (id 7): không có gói còn hạn
 *   staff id 2; HLV id 3 làm Thứ 2 từ 06:00
 */
class NotificationAuditTest extends ApiTestSupport {

	private static final String MEMBER2 = "member2@fitness.local";
	private static final long MEMBER1_ID = 5;
	private static final long MEMBER3_ID = 7;

	private final LocalDateTime nextMonday7am = LocalDate.now().plusDays(7)
			.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).atTime(7, 0);

	@Autowired
	NotificationService notificationService;

	@Autowired
	SubscriptionRepository subscriptionRepository;

	@Test
	void booking_createsNotification_andReadFlowWorks() throws Exception {
		long bookingId = readJson(bookPt(MEMBER1).andExpect(status().isCreated())).get("id").asLong();

		JsonNode latest = readJson(getAs(MEMBER1, "/api/notifications/me")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].title").value("Đặt lịch thành công"))
				.andExpect(jsonPath("$.content[0].content", containsString("buổi PT với HLV Nguyễn Tuấn Anh")))
				.andExpect(jsonPath("$.content[0].referenceType").value("BOOKING"))
				.andExpect(jsonPath("$.content[0].referenceId").value(bookingId))
				.andExpect(jsonPath("$.content[0].read").value(false))).at("/content/0");
		unreadCount(MEMBER1).andExpect(jsonPath("$.count").value(2));

		long notificationId = latest.get("id").asLong();
		mvc.perform(auth(put("/api/notifications/" + notificationId + "/read"), MEMBER1))
				.andExpect(status().isNoContent());
		unreadCount(MEMBER1).andExpect(jsonPath("$.count").value(1));

		// Không đọc được thông báo của người khác
		mvc.perform(auth(put("/api/notifications/" + notificationId + "/read"), MEMBER2))
				.andExpect(status().isNotFound());

		mvc.perform(auth(put("/api/notifications/read-all"), MEMBER1))
				.andExpect(jsonPath("$.count").value(1));
		unreadCount(MEMBER1).andExpect(jsonPath("$.count").value(0));

		// Hủy lịch -> thêm thông báo hủy
		mvc.perform(auth(delete("/api/bookings/" + bookingId), MEMBER1)).andExpect(status().isOk());
		getAs(MEMBER1, "/api/notifications/me")
				.andExpect(jsonPath("$.content[0].title").value("Đã hủy lịch tập"));
	}

	@Test
	void paymentConfirm_notifiesMember_andIsAudited() throws Exception {
		long paymentId = readJson(postAs(MEMBER3, "/api/subscriptions", "{\"packageId\":1,\"method\":\"CASH\"}")
				.andExpect(status().isCreated())).at("/payment/id").asLong();
		postAs(STAFF, "/api/payments/" + paymentId + "/confirm", "").andExpect(status().isOk());

		getAs(MEMBER3, "/api/notifications/me")
				.andExpect(jsonPath("$.content[0].type").value("PAYMENT"))
				.andExpect(jsonPath("$.content[0].title").value("Thanh toán thành công"))
				.andExpect(jsonPath("$.content[0].content", containsString("1.200.000đ cho Gói 1 tháng")));

		auditLogs("action=PAYMENT_CONFIRM&entityId=" + paymentId)
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].actorEmail").value(STAFF))
				.andExpect(jsonPath("$.content[0].entityType").value("PAYMENT"));
		auditLogs("action=SUBSCRIPTION_PURCHASE&actorId=" + MEMBER3_ID)
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].newValue.packageId").value(1))
				.andExpect(jsonPath("$.content[0].newValue.method").value("CASH"));
	}

	@Test
	void login_isAudited_andOnlyAdminCanReadLogs() throws Exception {
		tokenOf(MEMBER1);
		auditLogs("action=login&actorId=" + MEMBER1_ID)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.content[0].entityType").value("USER"))
				.andExpect(jsonPath("$.content[0].ipAddress").isNotEmpty());

		getAs(MEMBER1, "/api/audit-logs").andExpect(status().isForbidden());
		getAs(ADMIN, "/api/audit-logs/999999").andExpect(status().isNotFound());
	}

	@Test
	void failedAction_isNotAudited() throws Exception {
		// member3 không có gói còn hạn -> đặt lịch thất bại -> không có log BOOKING_CREATE
		bookPt(MEMBER3).andExpect(status().isBadRequest());
		auditLogs("action=BOOKING_CREATE&actorId=" + MEMBER3_ID).andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void broadcast_reachesRole_andIsAudited() throws Exception {
		String body = """
				{"title":"Ưu đãi tháng 11","content":"Giảm 20% gói 6 tháng","type":"PROMOTION","role":"MEMBER"}
				""";
		postAs(MEMBER1, "/api/notifications/broadcast", body).andExpect(status().isForbidden());
		postAs(ADMIN, "/api/notifications/broadcast", body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(3));

		getAs(MEMBER2, "/api/notifications/me")
				.andExpect(jsonPath("$.content[0].title").value("Ưu đãi tháng 11"));
		getAs(STAFF, "/api/notifications/me")
				.andExpect(jsonPath("$.content.length()").value(0));
		auditLogs("action=NOTIFICATION_BROADCAST")
				.andExpect(jsonPath("$.content[0].newValue.recipients").value(3));
	}

	@Test
	void expiringReminder_isSentOncePerSubscription() throws Exception {
		LocalDate endDate = subscriptionRepository.findById(1L).orElseThrow().getEndDate();

		assertTrue(notificationService.remindExpiring(endDate) >= 1);
		getAs(MEMBER1, "/api/notifications/me")
				.andExpect(jsonPath("$.content[0].title").value("Gói tập sắp hết hạn"))
				.andExpect(jsonPath("$.content[0].type").value("MEMBERSHIP"));

		assertEquals(0, notificationService.remindExpiring(endDate));
	}

	private ResultActions bookPt(String email) throws Exception {
		return postAs(email, "/api/bookings/pt", "{\"trainerId\":3,\"startAt\":\"%s\"}".formatted(nextMonday7am));
	}

	private ResultActions unreadCount(String email) throws Exception {
		return getAs(email, "/api/notifications/me/unread-count");
	}

	private ResultActions auditLogs(String query) throws Exception {
		return mvc.perform(auth(get("/api/audit-logs?" + query), ADMIN));
	}
}
