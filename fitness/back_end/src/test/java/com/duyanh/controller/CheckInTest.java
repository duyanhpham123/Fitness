package com.duyanh.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Dữ liệu mẫu: member1 (id 5) có gói 3 tháng còn hạn, member2 (id 6) có gói 6 tháng,
 * member3 (id 7) không có gói còn hạn; staff id 2.
 */
class CheckInTest extends ApiTestSupport {

	private static final String MEMBER2 = "member2@fitness.local";
	private static final long MEMBER2_ID = 6;
	private static final long MEMBER3_ID = 7;

	@Test
	void qrCheckIn_thenCheckOut_thenCheckInAgain() throws Exception {
		String qr = qrOf(MEMBER1);

		long checkInId = readJson(scan(qr)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.memberName").value("Phạm Duy Anh"))
				.andExpect(jsonPath("$.method").value("QR"))
				.andExpect(jsonPath("$.packageName").value("Gói 3 tháng"))
				.andExpect(jsonPath("$.staffName").value("Trần Thị Lễ Tân"))
				.andExpect(jsonPath("$.checkedOutAt").isEmpty())).get("id").asLong();

		// Mã QR chỉ dùng 1 lần
		scan(qr).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QR_INVALID"));
		// Đang ở phòng tập thì không check-in lần 2
		scan(qrOf(MEMBER1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"));

		postAs(STAFF, "/api/check-ins/" + checkInId + "/check-out", "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkedOutAt").isNotEmpty());
		postAs(STAFF, "/api/check-ins/" + checkInId + "/check-out", "")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_CHECKED_OUT"));

		scan(qrOf(MEMBER1)).andExpect(status().isCreated());
		getAs(MEMBER1, "/api/check-ins/me?size=2")
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].method").value("QR"));
	}

	@Test
	void manualCheckIn_requiresActiveSubscription() throws Exception {
		postAs(STAFF, "/api/check-ins/manual", "{\"memberId\":%d}".formatted(MEMBER3_ID))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("NO_ACTIVE_SUBSCRIPTION"));

		postAs(STAFF, "/api/check-ins/manual", "{\"memberId\":%d}".formatted(MEMBER2_ID))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.method").value("MANUAL"))
				.andExpect(jsonPath("$.memberName").value("Trần Thị Mai"));

		// Staff (id 2) không phải hội viên
		postAs(STAFF, "/api/check-ins/manual", "{\"memberId\":2}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	@Test
	void onlyStaffCanScan_onlyMemberGetsQr_andActionsAreAudited() throws Exception {
		String qr = qrOf(MEMBER2);
		postAs(MEMBER1, "/api/check-ins", "{\"qrToken\":\"%s\"}".formatted(qr)).andExpect(status().isForbidden());
		getAs(STAFF, "/api/check-ins/me/qr").andExpect(status().isForbidden());

		scan(qr).andExpect(status().isCreated());
		getAs(ADMIN, "/api/audit-logs?action=CHECK_IN&actorId=2")
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].newValue.method").value("QR"))
				.andExpect(jsonPath("$.content[0].newValue.memberId").value(MEMBER2_ID));
	}

	private String qrOf(String member) throws Exception {
		return readJson(getAs(member, "/api/check-ins/me/qr")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.expiresIn").value(60))).get("qrToken").asText();
	}

	private ResultActions scan(String qrToken) throws Exception {
		return postAs(STAFF, "/api/check-ins", "{\"qrToken\":\"%s\"}".formatted(qrToken));
	}
}
