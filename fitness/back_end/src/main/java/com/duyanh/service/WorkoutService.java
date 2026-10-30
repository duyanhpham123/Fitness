package com.duyanh.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.audit.AuditActions;
import com.duyanh.audit.Auditable;
import com.duyanh.dto.workout.ExerciseRequest;
import com.duyanh.dto.workout.ExerciseResponse;
import com.duyanh.dto.workout.WorkoutPlanRequest;
import com.duyanh.dto.workout.WorkoutPlanResponse;
import com.duyanh.entity.Exercise;
import com.duyanh.entity.WorkoutPlan;
import com.duyanh.entity.WorkoutPlanDay;
import com.duyanh.entity.WorkoutPlanExercise;
import com.duyanh.entity.enums.MuscleGroup;
import com.duyanh.entity.enums.RoleCode;
import com.duyanh.entity.enums.WorkoutPlanStatus;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.WorkoutMapper;
import com.duyanh.repository.ExerciseRepository;
import com.duyanh.repository.TrainerProfileRepository;
import com.duyanh.repository.UserRepository;
import com.duyanh.repository.WorkoutPlanRepository;
import com.duyanh.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

/**
 * Bài tập và kế hoạch tập.
 *
 * Quyền: HLV soạn kế hoạch cho hội viên và chỉ sửa được kế hoạch mình soạn; hội viên tự soạn
 * kế hoạch cho mình. Chỉ hội viên sở hữu kế hoạch mới đánh dấu hoàn thành ngày tập.
 */
@Service
@RequiredArgsConstructor
public class WorkoutService {

	private static final List<WorkoutPlanStatus> VISIBLE_STATUSES =
			List.of(WorkoutPlanStatus.ACTIVE, WorkoutPlanStatus.COMPLETED);

	private final ExerciseRepository exerciseRepository;
	private final WorkoutPlanRepository planRepository;
	private final UserRepository userRepository;
	private final TrainerProfileRepository trainerRepository;
	private final WorkoutMapper mapper;

	@Transactional(readOnly = true)
	public List<ExerciseResponse> listExercises(MuscleGroup muscleGroup) {
		List<Exercise> exercises = muscleGroup == null
				? exerciseRepository.findAllByOrderByMuscleGroupAscNameAsc()
				: exerciseRepository.findByMuscleGroupOrderByNameAsc(muscleGroup);
		return exercises.stream().map(mapper::toResponse).toList();
	}

	@Auditable(action = AuditActions.EXERCISE_CREATE, entityType = "EXERCISE", entityId = "#result.id()",
			details = "{'name': #req.name()}")
	@Transactional
	public ExerciseResponse createExercise(ExerciseRequest req) {
		String name = req.name().trim();
		if (exerciseRepository.existsByName(name)) {
			throw ApiException.conflict("EXERCISE_NAME_EXISTS", "Bài tập đã tồn tại");
		}
		Exercise e = new Exercise();
		e.setName(name);
		e.setMuscleGroup(req.muscleGroup());
		e.setEquipment(req.equipment());
		e.setDescription(req.description());
		e.setImageUrl(req.imageUrl());
		e.setVideoUrl(req.videoUrl());
		return mapper.toResponse(exerciseRepository.save(e));
	}

	/**
	 * HLV tạo cho hội viên (memberId bắt buộc), hoặc hội viên tự tạo cho mình (memberId bỏ qua).
	 */
	@Auditable(action = AuditActions.WORKOUT_PLAN_CREATE, entityType = "WORKOUT_PLAN", entityId = "#result.id()",
			details = "{'memberId': #result.memberId(), 'days': #result.totalDays()}")
	@Transactional
	public WorkoutPlanResponse createPlan(WorkoutPlanRequest req, UserPrincipal author) {
		WorkoutPlan plan = new WorkoutPlan();
		if (isTrainer(author)) {
			if (req.memberId() == null) {
				throw ApiException.badRequest("MEMBER_REQUIRED", "Chưa chọn hội viên cho kế hoạch");
			}
			plan.setMember(userRepository.findById(req.memberId())
					.filter(u -> u.getRoles().stream().anyMatch(r -> r.getCode() == RoleCode.MEMBER))
					.orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "Không tìm thấy hội viên")));
			plan.setTrainer(trainerRepository.findByIdAndActiveTrue(author.getId())
					.orElseThrow(() -> ApiException.forbidden("TRAINER_INACTIVE", "Hồ sơ HLV chưa được kích hoạt")));
		} else {
			plan.setMember(userRepository.getReferenceById(author.getId()));
		}
		applyContent(plan, req);
		return mapper.toResponse(planRepository.save(plan));
	}

	/**
	 * Thay toàn bộ nội dung kế hoạch (tên, mục tiêu, ngày tập). Ngày tập đã hoàn thành cũng bị thay mới.
	 */
	@Auditable(action = AuditActions.WORKOUT_PLAN_UPDATE, entityType = "WORKOUT_PLAN", entityId = "#planId")
	@Transactional
	public WorkoutPlanResponse updatePlan(Long planId, WorkoutPlanRequest req, UserPrincipal editor) {
		WorkoutPlan plan = findVisible(planId, editor);
		boolean canEdit = plan.getTrainer() != null
				? plan.getTrainer().getId().equals(editor.getId())
				: plan.getMember().getId().equals(editor.getId());
		if (!canEdit) {
			throw ApiException.forbidden("PLAN_NOT_EDITABLE",
					"Chỉ người soạn kế hoạch mới được sửa (kế hoạch do HLV soạn thì liên hệ HLV)");
		}
		// Xóa ngày cũ và ghi xuống DB trước khi thêm ngày mới: Hibernate mặc định insert trước delete,
		// ngày mới trùng day_number với ngày cũ sẽ đụng unique key (plan_id, day_number)
		plan.getDays().clear();
		planRepository.saveAndFlush(plan);
		applyContent(plan, req);
		plan.setStatus(WorkoutPlanStatus.ACTIVE);
		return mapper.toResponse(planRepository.saveAndFlush(plan));
	}

	@Transactional(readOnly = true)
	public List<WorkoutPlanResponse> listMine(Long memberId) {
		return planRepository.findByMemberIdAndStatusInOrderByCreatedAtDesc(memberId, VISIBLE_STATUSES).stream()
				.map(mapper::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<WorkoutPlanResponse> listCreatedByTrainer(Long trainerId) {
		return planRepository.findByTrainerIdOrderByCreatedAtDesc(trainerId).stream().map(mapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public WorkoutPlanResponse get(Long planId, UserPrincipal viewer) {
		return mapper.toResponse(findVisible(planId, viewer));
	}

	/**
	 * Hội viên đánh dấu đã tập xong 1 ngày. Xong hết các ngày thì kế hoạch chuyển COMPLETED.
	 */
	@Transactional
	public WorkoutPlanResponse completeDay(Long planId, Long dayId, Long memberId) {
		WorkoutPlan plan = planRepository.findById(planId)
				.filter(p -> p.getMember().getId().equals(memberId))
				.orElseThrow(WorkoutService::planNotFound);
		if (plan.getStatus() == WorkoutPlanStatus.ARCHIVED) {
			throw ApiException.conflict("PLAN_ARCHIVED", "Kế hoạch đã lưu trữ");
		}
		WorkoutPlanDay day = plan.getDays().stream()
				.filter(d -> d.getId().equals(dayId))
				.findFirst()
				.orElseThrow(() -> ApiException.notFound("DAY_NOT_FOUND", "Không tìm thấy ngày tập"));
		if (day.getCompletedAt() != null) {
			throw ApiException.conflict("DAY_ALREADY_COMPLETED", "Ngày tập này đã được đánh dấu hoàn thành");
		}
		day.setCompletedAt(LocalDateTime.now());
		if (plan.getDays().stream().allMatch(d -> d.getCompletedAt() != null)) {
			plan.setStatus(WorkoutPlanStatus.COMPLETED);
		}
		return mapper.toResponse(plan);
	}

	private void applyContent(WorkoutPlan plan, WorkoutPlanRequest req) {
		if (req.startDate() != null && req.endDate() != null && req.endDate().isBefore(req.startDate())) {
			throw ApiException.badRequest("INVALID_DATES", "Ngày kết thúc phải sau ngày bắt đầu");
		}
		Set<Integer> dayNumbers = new HashSet<>();
		for (WorkoutPlanRequest.Day d : req.days()) {
			if (!dayNumbers.add(d.dayNumber())) {
				throw ApiException.badRequest("DUPLICATE_DAY", "Ngày tập %d bị trùng".formatted(d.dayNumber()));
			}
			for (WorkoutPlanRequest.Item item : d.exercises()) {
				if (item.reps() == null && item.durationSeconds() == null) {
					throw ApiException.badRequest("INVALID_EXERCISE_ITEM",
							"Ngày %d: mỗi bài cần số lần (reps) hoặc thời gian (durationSeconds)".formatted(d.dayNumber()));
				}
			}
		}
		Set<Long> exerciseIds = req.days().stream()
				.flatMap(d -> d.exercises().stream())
				.map(WorkoutPlanRequest.Item::exerciseId)
				.collect(Collectors.toSet());
		Map<Long, Exercise> exercises = exerciseRepository.findAllById(exerciseIds).stream()
				.collect(Collectors.toMap(Exercise::getId, Function.identity()));
		if (exercises.size() != exerciseIds.size()) {
			throw ApiException.badRequest("EXERCISE_NOT_FOUND", "Có bài tập không tồn tại trong danh sách");
		}

		plan.setName(req.name().trim());
		plan.setGoal(req.goal());
		plan.setStartDate(req.startDate());
		plan.setEndDate(req.endDate());
		for (WorkoutPlanRequest.Day d : req.days()) {
			WorkoutPlanDay day = new WorkoutPlanDay();
			day.setDayNumber(d.dayNumber());
			day.setTitle(d.title().trim());
			int order = 1;
			for (WorkoutPlanRequest.Item i : d.exercises()) {
				WorkoutPlanExercise item = new WorkoutPlanExercise();
				item.setExercise(exercises.get(i.exerciseId()));
				item.setSortOrder(order++);
				item.setSets(i.sets());
				item.setReps(i.reps());
				item.setDurationSeconds(i.durationSeconds());
				item.setRestSeconds(i.restSeconds());
				item.setWeightKg(i.weightKg());
				item.setNote(i.note());
				day.addExercise(item);
			}
			plan.addDay(day);
		}
		plan.getDays().sort((a, b) -> a.getDayNumber().compareTo(b.getDayNumber()));
	}

	/**
	 * Hội viên sở hữu, HLV soạn kế hoạch, hoặc ADMIN được xem.
	 */
	private WorkoutPlan findVisible(Long planId, UserPrincipal viewer) {
		return planRepository.findById(planId)
				.filter(p -> p.getMember().getId().equals(viewer.getId())
						|| (p.getTrainer() != null && p.getTrainer().getId().equals(viewer.getId()))
						|| viewer.getRoles().contains("ADMIN"))
				.orElseThrow(WorkoutService::planNotFound);
	}

	private static boolean isTrainer(UserPrincipal user) {
		return user.getRoles().contains("TRAINER");
	}

	private static ApiException planNotFound() {
		return ApiException.notFound("PLAN_NOT_FOUND", "Không tìm thấy kế hoạch tập");
	}
}
