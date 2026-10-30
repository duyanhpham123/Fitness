package com.duyanh.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Dữ liệu mẫu dùng trong test (02_seed.sql):
 *   HLV id 3 (trainer1): Thứ 2 làm 06:00-11:00 và 15:00-21:00 -> 11 slot PT 60 phút
 *   HLV id 4 (trainer2)
 *   member1: gói id 1 còn 1 buổi PT; member2: gói id 2 còn 2 buổi PT; member3: không có gói còn hạn
 *   lớp id 3 = Zumba 60 phút
 * Ngày test là Thứ 2 cách hôm nay ít nhất 7 ngày, để kết quả không phụ thuộc giờ chạy test.
 */
class BookingControllerTest extends ApiTestSupport {

	private static final String MEMBER2 = "member2@fitness.local";
	private static final String TRAINER1 = "trainer1@fitness.local";
	private static final long TRAINER1_ID = 3;
	private static final long TRAINER2_ID = 4;
	private static final long ZUMBA = 3;

	private final LocalDate monday = LocalDate.now().plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

	@Test
	void trainers_arePublic_withScheduleAndSlots() throws Exception {
		mvc.perform(get("/api/trainers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
		mvc.perform(get("/api/trainers/" + TRAINER1_ID))
				.andExpect(jsonPath("$.profile.fullName").value("Nguyễn Tuấn Anh"))
				.andExpect(jsonPath("$.schedule.length()").value(11));
		slots(TRAINER1_ID)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(11))
				.andExpect(jsonPath("$[0].startAt").value(at(6, 0).toString() + ":00"));

		mvc.perform(get("/api/trainers/abc")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/trainers/999")).andExpect(status().isNotFound());
	}

	@Test
	void bookPt_usesPtSession_andSlotDisappears() throws Exception {
		bookPt(MEMBER1, TRAINER1_ID, at(7, 0), "key-pt-1")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("PT"))
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.trainerName").value("Nguyễn Tuấn Anh"))
				.andExpect(jsonPath("$.endAt").value(at(8, 0).toString() + ":00"));

		slots(TRAINER1_ID).andExpect(jsonPath("$.length()").value(10));
		ptSessionsOfMember1().andExpect(jsonPath("$[?(@.id == 1)].ptSessionsRemaining").value(0));

		// Gửi lại cùng Idempotency-Key -> cùng lịch, không trừ thêm buổi PT
		bookPt(MEMBER1, TRAINER1_ID, at(7, 0), "key-pt-1").andExpect(status().isCreated());

		// Người khác đặt cùng slot
		bookPt(MEMBER2, TRAINER1_ID, at(7, 0), null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));

		// member1 hết buổi PT
		bookPt(MEMBER1, TRAINER1_ID, at(9, 0), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("NO_PT_SESSIONS"));
	}

	@Test
	void bookPt_invalidSlotOrNoSubscription_isRejected() throws Exception {
		// Không đúng mốc slot, ngoài giờ làm, đã qua
		bookPt(MEMBER2, TRAINER1_ID, at(7, 30), null).andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
		bookPt(MEMBER2, TRAINER1_ID, at(12, 0), null).andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
		bookPt(MEMBER2, TRAINER1_ID, LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.HOURS), null)
				.andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));

		bookPt(MEMBER3, TRAINER1_ID, at(7, 0), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("NO_ACTIVE_SUBSCRIPTION"));

		bookPt(STAFF, TRAINER1_ID, at(7, 0), null).andExpect(status().isForbidden());
	}

	@Test
	void cancelPt_refundsSession_andFreesSlot() throws Exception {
		long id = readJson(bookPt(MEMBER1, TRAINER1_ID, at(7, 0), null)).get("id").asLong();

		mvc.perform(auth(delete("/api/bookings/" + id), MEMBER1).param("reason", "Bận việc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancelReason").value("Bận việc"));

		ptSessionsOfMember1().andExpect(jsonPath("$[?(@.id == 1)].ptSessionsRemaining").value(1));
		slots(TRAINER1_ID).andExpect(jsonPath("$.length()").value(11));
		// Slot đã hủy đặt lại được
		bookPt(MEMBER2, TRAINER1_ID, at(7, 0), null).andExpect(status().isCreated());

		mvc.perform(auth(delete("/api/bookings/" + id), MEMBER1))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BOOKING_NOT_CANCELLABLE"));
		// Không hủy được lịch của người khác
		mvc.perform(auth(delete("/api/bookings/" + id), MEMBER2)).andExpect(status().isNotFound());
	}

	@Test
	void bookClass_respectsCapacity_andCancelReleasesSeat() throws Exception {
		long sessionId = createSession(TRAINER2_ID, at(9, 0), 1).get("id").asLong();

		long bookingId = readJson(bookClass(MEMBER1, sessionId)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.className").value("Zumba"))).get("id").asLong();
		sessions().andExpect(jsonPath("$[?(@.id == %d)].availableSeats".formatted(sessionId)).value(0));

		bookClass(MEMBER1, sessionId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_BOOKED"));
		bookClass(MEMBER2, sessionId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CLASS_FULL"));

		mvc.perform(auth(delete("/api/bookings/" + bookingId), MEMBER1)).andExpect(status().isOk());
		sessions().andExpect(jsonPath("$[?(@.id == %d)].availableSeats".formatted(sessionId)).value(1));
		bookClass(MEMBER2, sessionId).andExpect(status().isCreated());
	}

	@Test
	void memberCannotDoubleBookSameTime() throws Exception {
		bookPt(MEMBER1, TRAINER1_ID, at(7, 0), null).andExpect(status().isCreated());
		long sessionId = createSession(TRAINER2_ID, at(7, 30), null).get("id").asLong();

		bookClass(MEMBER1, sessionId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MEMBER_TIME_CONFLICT"));
	}

	@Test
	void classSession_blocksTrainerSlots_andTrainerCannotBeDoubleBooked() throws Exception {
		createSession(TRAINER1_ID, at(7, 0), null);
		slots(TRAINER1_ID).andExpect(jsonPath("$.length()").value(10));

		postAs(ADMIN, "/api/class-sessions", sessionBody(TRAINER1_ID, at(7, 30), null))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRAINER_BUSY"));
		postAs(MEMBER1, "/api/class-sessions", sessionBody(TRAINER2_ID, at(7, 0), null))
				.andExpect(status().isForbidden());
	}

	@Test
	void cancelWithinTwoHours_isRejected() throws Exception {
		LocalDateTime soon = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.MINUTES);
		// HLV mới tạo, chắc chắn chưa có lịch: HLV mẫu có buổi học cố định trong dữ liệu mẫu,
		// "1 giờ nữa" có thể trùng tùy giờ chạy test
		long freeTrainerId = readJson(postAs(ADMIN, "/api/admin/users", """
				{"email":"free.trainer@fitness.local","password":"%s","fullName":"HLV Rảnh","role":"TRAINER"}
				""".formatted(SEED_PASSWORD)).andExpect(status().isCreated())).get("id").asLong();
		long sessionId = createSession(freeTrainerId, soon, null).get("id").asLong();
		long bookingId = readJson(bookClass(MEMBER1, sessionId).andExpect(status().isCreated())).get("id").asLong();

		mvc.perform(auth(delete("/api/bookings/" + bookingId), MEMBER1))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CANCEL_TOO_LATE"));
	}

	@Test
	void trainerSeesOwnBookings_andUpdatesSchedule() throws Exception {
		bookPt(MEMBER1, TRAINER1_ID, at(7, 0), null).andExpect(status().isCreated());
		mvc.perform(auth(get("/api/bookings/trainer/me"), TRAINER1)
				.param("from", monday.toString()).param("to", monday.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].memberName").value("Phạm Duy Anh"));

		// Ca chồng giờ -> lỗi
		putAs(TRAINER1, "/api/trainers/me/schedule", """
				{"items":[{"dayOfWeek":1,"startTime":"08:00","endTime":"12:00"},
				          {"dayOfWeek":1,"startTime":"11:00","endTime":"13:00"}]}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SCHEDULE_OVERLAP"));

		// Thứ 2 chỉ còn 08:00-10:00 -> 2 slot, trừ 07:00 đã đặt nằm ngoài lịch mới
		putAs(TRAINER1, "/api/trainers/me/schedule", """
				{"items":[{"dayOfWeek":1,"startTime":"08:00","endTime":"10:00"}]}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		slots(TRAINER1_ID).andExpect(jsonPath("$.length()").value(2));

		putAs(MEMBER1, "/api/trainers/me/schedule", "{\"items\":[]}").andExpect(status().isForbidden());
	}

	@Test
	void freeze_isBlocked_whenBookingsInFreezePeriod() throws Exception {
		bookPt(MEMBER1, TRAINER1_ID, at(7, 0), null).andExpect(status().isCreated());
		postAs(MEMBER1, "/api/subscriptions/1/freeze", "{\"days\":15}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("HAS_BOOKINGS_IN_FREEZE_PERIOD"));
	}

	@Test
	void myBookings_arePaged() throws Exception {
		bookPt(MEMBER1, TRAINER1_ID, at(7, 0), null).andExpect(status().isCreated());
		getAs(MEMBER1, "/api/bookings/me?size=1")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].startAt").value(at(7, 0).toString() + ":00"))
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	private LocalDateTime at(int hour, int minute) {
		return monday.atTime(hour, minute);
	}

	private ResultActions slots(long trainerId) throws Exception {
		return mvc.perform(get("/api/trainers/" + trainerId + "/slots").param("date", monday.toString()));
	}

	private ResultActions sessions() throws Exception {
		return mvc.perform(get("/api/class-sessions").param("date", monday.toString()));
	}

	private ResultActions ptSessionsOfMember1() throws Exception {
		return getAs(MEMBER1, "/api/subscriptions/me");
	}

	private ResultActions bookPt(String email, long trainerId, LocalDateTime startAt, String key) throws Exception {
		var req = auth(post("/api/bookings/pt"), email)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"trainerId\":%d,\"startAt\":\"%s\"}".formatted(trainerId, startAt));
		if (key != null) {
			req.header("Idempotency-Key", key);
		}
		return mvc.perform(req);
	}

	private ResultActions bookClass(String email, long sessionId) throws Exception {
		return postAs(email, "/api/bookings/class", "{\"classSessionId\":%d}".formatted(sessionId));
	}

	private com.fasterxml.jackson.databind.JsonNode createSession(long trainerId, LocalDateTime startAt,
			Integer capacity) throws Exception {
		return readJson(postAs(ADMIN, "/api/class-sessions", sessionBody(trainerId, startAt, capacity))
				.andExpect(status().isCreated()));
	}

	private static String sessionBody(long trainerId, LocalDateTime startAt, Integer capacity) {
		return "{\"classId\":%d,\"trainerId\":%d,\"startAt\":\"%s\",\"room\":\"Phòng B\"%s}".formatted(
				ZUMBA, trainerId, startAt, capacity == null ? "" : ",\"capacity\":" + capacity);
	}
}
