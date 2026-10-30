package com.duyanh.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.duyanh.entity.Booking;
import com.duyanh.repository.BookingRepository;
import com.duyanh.service.NotificationService;

/**
 * Đánh dấu đã tập / vắng, hủy buổi lớp, nhắc lịch, HLV sửa hồ sơ.
 * HLV id 3 (trainer1) làm Thứ 2 từ 06:00; HLV id 4 (trainer2); lớp id 3 = Zumba.
 */
class BookingLifecycleTest extends ApiTestSupport {

	private static final String MEMBER2 = "member2@fitness.local";
	private static final String TRAINER1 = "trainer1@fitness.local";
	private static final String TRAINER2 = "trainer2@fitness.local";

	private final LocalDate monday = LocalDate.now().plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

	@Autowired
	BookingRepository bookingRepository;

	@Autowired
	NotificationService notificationService;

	@Test
	void trainerMarksOwnPtBooking_afterItStarts() throws Exception {
		long bookingId = bookPt(MEMBER1, monday.atTime(7, 0));

		// Chưa tới giờ tập
		putAs(TRAINER1, attendance(bookingId), "{\"status\":\"COMPLETED\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SESSION_NOT_STARTED"));

		// Giả lập buổi tập đã bắt đầu 1 giờ trước
		Booking b = bookingRepository.findById(bookingId).orElseThrow();
		b.setStartAt(LocalDateTime.now().minusHours(1));
		b.setEndAt(LocalDateTime.now());
		bookingRepository.saveAndFlush(b);

		putAs(TRAINER2, attendance(bookingId), "{\"status\":\"COMPLETED\"}").andExpect(status().isNotFound());
		putAs(MEMBER1, attendance(bookingId), "{\"status\":\"COMPLETED\"}").andExpect(status().isForbidden());
		putAs(TRAINER1, attendance(bookingId), "{\"status\":\"CANCELLED\"}")
				.andExpect(jsonPath("$.code").value("INVALID_STATUS"));

		putAs(TRAINER1, attendance(bookingId), "{\"status\":\"NO_SHOW\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("NO_SHOW"));
		putAs(STAFF, attendance(bookingId), "{\"status\":\"COMPLETED\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BOOKING_NOT_MARKABLE"));
	}

	@Test
	void cancelClassSession_cancelsBookings_andNotifiesMembersWithReason() throws Exception {
		long sessionId = readJson(postAs(ADMIN, "/api/class-sessions",
				"{\"classId\":3,\"trainerId\":4,\"startAt\":\"%s\",\"capacity\":10}".formatted(monday.atTime(9, 0)))
				.andExpect(status().isCreated())).get("id").asLong();
		postAs(MEMBER1, "/api/bookings/class", "{\"classSessionId\":%d}".formatted(sessionId)).andExpect(status().isCreated());
		postAs(MEMBER2, "/api/bookings/class", "{\"classSessionId\":%d}".formatted(sessionId)).andExpect(status().isCreated());

		postAs(MEMBER1, "/api/class-sessions/" + sessionId + "/cancel", "{\"reason\":\"x\"}")
				.andExpect(status().isForbidden());
		postAs(STAFF, "/api/class-sessions/" + sessionId + "/cancel", "{\"reason\":\"HLV bị ốm\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.bookedCount").value(0));

		getAs(MEMBER2, "/api/notifications/me")
				.andExpect(jsonPath("$.content[0].title").value("Đã hủy lịch tập"))
				.andExpect(jsonPath("$.content[0].content", containsString("Lý do: HLV bị ốm")));
		getAs(MEMBER1, "/api/bookings/me?size=1")
				.andExpect(jsonPath("$.content[0].status").value("CANCELLED"))
				.andExpect(jsonPath("$.content[0].cancelReason", containsString("HLV bị ốm")));

		postAs(STAFF, "/api/class-sessions/" + sessionId + "/cancel", "{\"reason\":\"lần 2\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SESSION_NOT_CANCELLABLE"));
		postAs(MEMBER1, "/api/bookings/class", "{\"classSessionId\":%d}".formatted(sessionId))
				.andExpect(jsonPath("$.code").value("SESSION_NOT_AVAILABLE"));
	}

	@Test
	void bookingReminder_isSentOncePerBooking() throws Exception {
		long bookingId = bookPt(MEMBER1, monday.atTime(7, 0));

		assertTrue(notificationService.remindBookings(monday) >= 1);
		getAs(MEMBER1, "/api/notifications/me")
				.andExpect(jsonPath("$.content[0].title").value("Nhắc lịch tập"))
				.andExpect(jsonPath("$.content[0].referenceId").value(bookingId))
				.andExpect(jsonPath("$.content[0].content", containsString("buổi PT với HLV Nguyễn Tuấn Anh")));

		assertEquals(0, notificationService.remindBookings(monday));
	}

	@Test
	void trainerUpdatesOwnProfile() throws Exception {
		putAs(TRAINER1, "/api/trainers/me", """
				{"specialization":"Gym, Powerlifting","bio":"10 năm kinh nghiệm","experienceYears":10}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.specialization").value("Gym, Powerlifting"));
		mvc.perform(get("/api/trainers/3"))
				.andExpect(jsonPath("$.profile.bio").value("10 năm kinh nghiệm"))
				.andExpect(jsonPath("$.profile.experienceYears").value(10));

		putAs(MEMBER1, "/api/trainers/me", "{\"experienceYears\":1}").andExpect(status().isForbidden());
	}

	private long bookPt(String email, LocalDateTime startAt) throws Exception {
		return readJson(postAs(email, "/api/bookings/pt", "{\"trainerId\":3,\"startAt\":\"%s\"}".formatted(startAt))
				.andExpect(status().isCreated())).get("id").asLong();
	}

	private static String attendance(long bookingId) {
		return "/api/bookings/" + bookingId + "/attendance";
	}
}
