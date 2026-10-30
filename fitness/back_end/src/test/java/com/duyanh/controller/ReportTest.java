package com.duyanh.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.duyanh.entity.CheckIn;
import com.duyanh.repository.CheckInRepository;
import com.duyanh.repository.SubscriptionRepository;
import com.duyanh.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Các test so sánh số liệu trước / sau một thao tác, nên không phụ thuộc dữ liệu đã có trong DB.
 * Dữ liệu mẫu: member1 (id 5) có gói id 1 đang ACTIVE, member3 chưa có gói còn hạn, gói id 1 = Gói 1 tháng.
 */
class ReportTest extends ApiTestSupport {

	private static final BigDecimal GYM_1M_PRICE = new BigDecimal("1200000");

	private final LocalDate today = LocalDate.now();

	@Autowired
	CheckInRepository checkInRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	SubscriptionRepository subscriptionRepository;

	@Test
	void overview_reflectsNewMemberPaymentAndBooking() throws Exception {
		JsonNode before = overview();

		createMember("report.member@fitness.local", "Hội viên Report");
		purchaseAndConfirm();
		bookPtNextMonday();

		JsonNode after = overview();
		assertIncreasedBy(before, after, "totalMembers", BigDecimal.ONE);
		assertIncreasedBy(before, after, "monthlyRevenue", GYM_1M_PRICE);
		assertIncreasedBy(before, after, "monthlyBookings", BigDecimal.ONE);
		assertIncreasedBy(before, after, "activeSubscriptions", BigDecimal.ONE);
	}

	// So sánh bằng compareTo: 4200000 và 4.2E+6 bằng nhau về giá trị nhưng khác scale
	private static void assertIncreasedBy(JsonNode before, JsonNode after, String metric, BigDecimal delta) {
		BigDecimal expected = value(before, metric).add(delta);
		BigDecimal actual = value(after, metric);
		assertEquals(0, expected.compareTo(actual), metric + ": expected " + expected + " but was " + actual);
	}

	@Test
	void revenue_isGroupedByDay_andIncludesNewPayment() throws Exception {
		String range = "/api/reports/revenue?from=%s&to=%s".formatted(today, today);
		BigDecimal before = new BigDecimal(readJson(getAs(ADMIN, range)).get("total").asText());

		purchaseAndConfirm();

		JsonNode after = readJson(getAs(ADMIN, range)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days.length()").value(1))
				.andExpect(jsonPath("$.days[0].date").value(today.toString())));
		assertEquals(0, before.add(GYM_1M_PRICE).compareTo(new BigDecimal(after.get("total").asText())));

		// Mặc định 7 ngày gần nhất, mỗi ngày 1 phần tử
		getAs(ADMIN, "/api/reports/revenue").andExpect(jsonPath("$.days.length()").value(7));
	}

	@Test
	void topPackages_countsSoldPackage() throws Exception {
		purchaseAndConfirm();
		getAs(ADMIN, "/api/reports/packages/top?from=%s&to=%s".formatted(today, today))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.packageId == 1)].packageName").value("Gói 1 tháng"))
				.andExpect(jsonPath("$[?(@.packageId == 1)].sold").value(org.hamcrest.Matchers.hasItem(
						org.hamcrest.Matchers.greaterThanOrEqualTo(1))));
	}

	@Test
	void attendance_countsCheckIns() throws Exception {
		String range = "/api/reports/attendance?from=%s&to=%s".formatted(today, today);
		long before = readJson(getAs(ADMIN, range)).get("attendedMembers").asLong();

		CheckIn checkIn = new CheckIn();
		checkIn.setUser(userRepository.findByEmail(MEMBER1).orElseThrow());
		checkIn.setSubscription(subscriptionRepository.findById(1L).orElseThrow());
		checkIn.setCheckedInAt(LocalDateTime.now());
		checkInRepository.save(checkIn);

		JsonNode after = readJson(getAs(ADMIN, range).andExpect(status().isOk()));
		assertTrue(after.get("attendedMembers").asLong() >= Math.min(before + 1, after.get("activeMembers").asLong()));
		assertTrue(after.at("/days/0/count").asLong() >= 1);
		assertTrue(after.get("rate").isNumber());
	}

	@Test
	void newMembers_listsLatestFirst() throws Exception {
		createMember("newest.member@fitness.local", "Hội viên Mới Nhất");
		getAs(ADMIN, "/api/reports/members/new?limit=3")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].fullName").value("Hội viên Mới Nhất"));
	}

	@Test
	void bookingsOfDay_visibleToStaff_revenueAdminOnly() throws Exception {
		LocalDate monday = bookPtNextMonday();
		getAs(STAFF, "/api/reports/bookings?date=" + monday)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.memberName == 'Phạm Duy Anh')].type").value("PT"));

		getAs(STAFF, "/api/reports/revenue").andExpect(status().isForbidden());
		getAs(MEMBER1, "/api/reports/bookings").andExpect(status().isForbidden());
	}

	@Test
	void invalidRange_isRejected() throws Exception {
		getAs(ADMIN, "/api/reports/revenue?from=%s&to=%s".formatted(today, today.minusDays(1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_RANGE"));
	}

	private JsonNode overview() throws Exception {
		return readJson(getAs(ADMIN, "/api/reports/overview").andExpect(status().isOk()));
	}

	private static BigDecimal value(JsonNode overview, String metric) {
		return new BigDecimal(overview.at("/" + metric + "/value").asText());
	}

	private void createMember(String email, String fullName) throws Exception {
		postAs(ADMIN, "/api/admin/users", """
				{"email":"%s","password":"%s","fullName":"%s","role":"MEMBER"}
				""".formatted(email, SEED_PASSWORD, fullName))
				.andExpect(status().isCreated());
	}

	// member3 mua gói 1 tháng trả tiền mặt, lễ tân xác nhận -> doanh thu hôm nay +1.200.000
	private void purchaseAndConfirm() throws Exception {
		long paymentId = readJson(postAs(MEMBER3, "/api/subscriptions", "{\"packageId\":1,\"method\":\"CASH\"}")
				.andExpect(status().isCreated())).at("/payment/id").asLong();
		postAs(STAFF, "/api/payments/" + paymentId + "/confirm", "").andExpect(status().isOk());
	}

	private LocalDate bookPtNextMonday() throws Exception {
		LocalDate monday = today.plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
		postAs(MEMBER1, "/api/bookings/pt", "{\"trainerId\":3,\"startAt\":\"%s\"}".formatted(monday.atTime(7, 0)))
				.andExpect(status().isCreated());
		return monday;
	}
}
