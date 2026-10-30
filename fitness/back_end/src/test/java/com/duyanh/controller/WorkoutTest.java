package com.duyanh.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Dữ liệu mẫu: bài tập id 1-4 (ngực, tay), 9 = Plank (tính theo thời gian);
 * kế hoạch id 1 của member1 do HLV id 3 (trainer1) soạn, 3 ngày (ngày id 1 đã hoàn thành, id 2, 3 chưa).
 */
class WorkoutTest extends ApiTestSupport {

	private static final String MEMBER2 = "member2@fitness.local";
	private static final String TRAINER1 = "trainer1@fitness.local";
	private static final String TRAINER2 = "trainer2@fitness.local";

	private static final String PLAN_FOR_MEMBER2 = """
			{"memberId":6,"name":"Giảm mỡ 2 tuần","goal":"Giảm mỡ","days":[
			  {"dayNumber":1,"title":"Ngực","exercises":[{"exerciseId":1,"sets":4,"reps":10,"restSeconds":90},
			                                           {"exerciseId":2,"sets":3,"reps":12}]},
			  {"dayNumber":2,"title":"Core","exercises":[{"exerciseId":9,"sets":3,"durationSeconds":60}]}]}
			""";

	@Test
	void exercises_filterByMuscle_andOnlyStaffRolesCanAdd() throws Exception {
		getAs(MEMBER1, "/api/exercises?muscleGroup=CHEST")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));

		String body = "{\"name\":\"Lat Pulldown\",\"muscleGroup\":\"BACK\",\"equipment\":\"Cable\"}";
		postAs(MEMBER1, "/api/exercises", body).andExpect(status().isForbidden());
		postAs(TRAINER1, "/api/exercises", body).andExpect(status().isCreated());
		postAs(TRAINER1, "/api/exercises", body)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EXERCISE_NAME_EXISTS"));
		mvc.perform(get("/api/exercises")).andExpect(status().isUnauthorized());
	}

	@Test
	void trainerCreatesPlanForMember_memberSeesIt_othersCannot() throws Exception {
		long planId = readJson(postAs(TRAINER1, "/api/workout-plans", PLAN_FOR_MEMBER2)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.memberName").value("Trần Thị Mai"))
				.andExpect(jsonPath("$.trainerName").value("Nguyễn Tuấn Anh"))
				.andExpect(jsonPath("$.totalDays").value(2))
				.andExpect(jsonPath("$.days[0].exercises[1].exerciseName").value("Incline Dumbbell Press"))
				.andExpect(jsonPath("$.days[0].exercises[1].sortOrder").value(2))
				.andExpect(jsonPath("$.days[1].exercises[0].durationSeconds").value(60))).get("id").asLong();

		getAs(MEMBER2, "/api/workout-plans/me")
				.andExpect(jsonPath("$[?(@.id == %d)].name".formatted(planId)).value("Giảm mỡ 2 tuần"));
		getAs(MEMBER1, "/api/workout-plans/" + planId).andExpect(status().isNotFound());
		getAs(TRAINER1, "/api/workout-plans/trainer/me")
				.andExpect(jsonPath("$[?(@.id == %d)].memberName".formatted(planId)).value("Trần Thị Mai"));

		// Hội viên không sửa được kế hoạch do HLV soạn; HLV khác cũng không xem được
		putAs(MEMBER2, "/api/workout-plans/" + planId, PLAN_FOR_MEMBER2)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PLAN_NOT_EDITABLE"));
		getAs(TRAINER2, "/api/workout-plans/" + planId).andExpect(status().isNotFound());
	}

	@Test
	void trainerUpdatesPlan_replacingDaysWithSameNumbers() throws Exception {
		long planId = readJson(postAs(TRAINER1, "/api/workout-plans", PLAN_FOR_MEMBER2)).get("id").asLong();

		putAs(TRAINER1, "/api/workout-plans/" + planId, """
				{"memberId":6,"name":"Giảm mỡ (sửa)","days":[
				  {"dayNumber":1,"title":"Toàn thân","exercises":[{"exerciseId":5,"sets":4,"reps":8}]}]}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Giảm mỡ (sửa)"))
				.andExpect(jsonPath("$.totalDays").value(1))
				.andExpect(jsonPath("$.days[0].title").value("Toàn thân"))
				.andExpect(jsonPath("$.days[0].exercises[0].exerciseName").value("Barbell Squat"));
	}

	@Test
	void memberCreatesOwnPlan_withoutTrainer() throws Exception {
		postAs(MEMBER2, "/api/workout-plans", """
				{"name":"Tự tập","days":[{"dayNumber":1,"title":"Cardio","exercises":[{"exerciseId":10,"durationSeconds":1200}]}]}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.memberName").value("Trần Thị Mai"))
				.andExpect(jsonPath("$.trainerId").isEmpty());
	}

	@Test
	void invalidPlans_areRejected() throws Exception {
		// HLV phải chọn hội viên
		postAs(TRAINER1, "/api/workout-plans", PLAN_FOR_MEMBER2.replace("\"memberId\":6,", ""))
				.andExpect(jsonPath("$.code").value("MEMBER_REQUIRED"));
		postAs(MEMBER2, "/api/workout-plans", """
				{"name":"Trùng ngày","days":[
				  {"dayNumber":1,"title":"A","exercises":[{"exerciseId":1,"reps":10}]},
				  {"dayNumber":1,"title":"B","exercises":[{"exerciseId":2,"reps":10}]}]}
				""")
				.andExpect(jsonPath("$.code").value("DUPLICATE_DAY"));
		postAs(MEMBER2, "/api/workout-plans", """
				{"name":"Bài không tồn tại","days":[{"dayNumber":1,"title":"A","exercises":[{"exerciseId":9999,"reps":10}]}]}
				""")
				.andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"));
		postAs(MEMBER2, "/api/workout-plans", """
				{"name":"Thiếu reps","days":[{"dayNumber":1,"title":"A","exercises":[{"exerciseId":1,"sets":3}]}]}
				""")
				.andExpect(jsonPath("$.code").value("INVALID_EXERCISE_ITEM"));
		postAs(MEMBER2, "/api/workout-plans", "{\"name\":\"Rỗng\",\"days\":[]}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.days").exists());
	}

	@Test
	void completeDays_finishesPlan() throws Exception {
		// Tự tạo kế hoạch 2 ngày cho member2 (không dùng kế hoạch mẫu: trạng thái của nó đổi khi thử trên giao diện)
		JsonNode plan = readJson(postAs(TRAINER1, "/api/workout-plans", PLAN_FOR_MEMBER2).andExpect(status().isCreated()));
		String base = "/api/workout-plans/" + plan.get("id").asLong() + "/days/";
		long day1 = plan.at("/days/0/id").asLong();
		long day2 = plan.at("/days/1/id").asLong();

		postAs(MEMBER2, base + day1 + "/complete", "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedDays").value(1))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
		postAs(MEMBER2, base + day1 + "/complete", "")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DAY_ALREADY_COMPLETED"));

		postAs(MEMBER2, base + day2 + "/complete", "")
				.andExpect(jsonPath("$.completedDays").value(2))
				.andExpect(jsonPath("$.status").value("COMPLETED"));

		// Kế hoạch của người khác
		postAs(MEMBER1, base + day2 + "/complete", "").andExpect(status().isNotFound());
	}

	@Test
	void bodyMetrics_upsertPerDay_withBmi() throws Exception {
		putAs(MEMBER1, "/api/body-metrics/me", "{\"weightKg\":70.5,\"heightCm\":172}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.measuredAt").value(LocalDate.now().toString()))
				.andExpect(jsonPath("$.bmi").value(23.8));
		// Cùng ngày -> cập nhật, giữ chiều cao cũ
		putAs(MEMBER1, "/api/body-metrics/me", "{\"weightKg\":70.0,\"bodyFatPct\":18.5}")
				.andExpect(jsonPath("$.heightCm").value(172.0))
				.andExpect(jsonPath("$.bmi").value(23.7));

		getAs(MEMBER1, "/api/body-metrics/me")
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[2].bodyFatPct").value(18.5));

		putAs(MEMBER1, "/api/body-metrics/me", "{\"measuredAt\":\"%s\",\"weightKg\":70}".formatted(
				LocalDate.now().plusDays(1)))
				.andExpect(status().isBadRequest());
		putAs(MEMBER1, "/api/body-metrics/me", "{\"note\":\"chỉ ghi chú\"}")
				.andExpect(jsonPath("$.code").value("EMPTY_METRIC"));
	}
}
