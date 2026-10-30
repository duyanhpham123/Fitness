package com.duyanh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.config.BookingProperties;
import com.duyanh.audit.AuditActions;
import com.duyanh.audit.Auditable;
import com.duyanh.dto.trainer.ScheduleItem;
import com.duyanh.dto.trainer.SlotResponse;
import com.duyanh.dto.trainer.TrainerDetailResponse;
import com.duyanh.dto.trainer.TrainerResponse;
import com.duyanh.entity.TrainerProfile;
import com.duyanh.entity.TrainerSchedule;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.TrainerMapper;
import com.duyanh.repository.BookingRepository;
import com.duyanh.repository.ClassSessionRepository;
import com.duyanh.repository.TrainerProfileRepository;
import com.duyanh.repository.TrainerScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TrainerService {

	private final TrainerProfileRepository trainerRepository;
	private final TrainerScheduleRepository scheduleRepository;
	private final BookingRepository bookingRepository;
	private final ClassSessionRepository classSessionRepository;
	private final BookingProperties bookingProperties;
	private final TrainerMapper mapper;

	@Transactional(readOnly = true)
	public List<TrainerResponse> list() {
		return trainerRepository.findByActiveTrue().stream().map(mapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public TrainerDetailResponse get(Long trainerId) {
		TrainerProfile trainer = requireActive(trainerId);
		return new TrainerDetailResponse(mapper.toResponse(trainer), scheduleOf(trainerId));
	}

	/**
	 * HLV thay toàn bộ lịch làm việc hàng tuần. Lịch đã được đặt trước đó vẫn giữ nguyên.
	 */
	@Auditable(action = AuditActions.SCHEDULE_UPDATE, entityType = "TRAINER", entityId = "#trainerId",
			details = "{'items': #items}")
	@Transactional
	public List<ScheduleItem> replaceSchedule(Long trainerId, List<ScheduleItem> items) {
		TrainerProfile trainer = trainerRepository.findById(trainerId)
				.orElseThrow(() -> ApiException.notFound("TRAINER_NOT_FOUND", "Không tìm thấy hồ sơ huấn luyện viên"));
		validateSchedule(items);

		scheduleRepository.deleteAllOfTrainer(trainerId);
		List<TrainerSchedule> entities = items.stream().map(item -> {
			TrainerSchedule s = new TrainerSchedule();
			s.setTrainer(trainer);
			s.setDayOfWeek(item.dayOfWeek());
			s.setStartTime(item.startTime());
			s.setEndTime(item.endTime());
			return s;
		}).toList();
		scheduleRepository.saveAll(entities);
		return scheduleOf(trainerId);
	}

	@Transactional(readOnly = true)
	public List<SlotResponse> availableSlots(Long trainerId, LocalDate date) {
		requireActive(trainerId);
		return computeAvailableSlots(trainerId, date);
	}

	/**
	 * Slot trống = lịch làm việc của ngày đó, chia theo bước pt-slot-minutes,
	 * bỏ các slot đã qua, trùng lịch PT đang hiệu lực hoặc trùng buổi lớp HLV đang dạy.
	 */
	List<SlotResponse> computeAvailableSlots(Long trainerId, LocalDate date) {
		int slotMinutes = bookingProperties.ptSlotMinutes();
		LocalDateTime dayStart = date.atStartOfDay();
		LocalDateTime dayEnd = dayStart.plusDays(1);

		List<LocalDateTime[]> busy = new ArrayList<>();
		bookingRepository.findActivePtOfTrainer(trainerId, dayStart, dayEnd)
				.forEach(b -> busy.add(new LocalDateTime[] { b.getStartAt(), b.getEndAt() }));
		classSessionRepository.findScheduledOfTrainer(trainerId, dayStart, dayEnd)
				.forEach(s -> busy.add(new LocalDateTime[] { s.getStartAt(), s.getEndAt() }));

		LocalDateTime now = LocalDateTime.now();
		List<SlotResponse> slots = new ArrayList<>();
		for (TrainerSchedule block : scheduleRepository
				.findByTrainerIdAndDayOfWeekOrderByStartTimeAsc(trainerId, date.getDayOfWeek().getValue())) {
			// Tính theo phút trong ngày để không bị vòng qua nửa đêm như LocalTime.plus
			int blockEnd = block.getEndTime().toSecondOfDay() / 60;
			for (int m = block.getStartTime().toSecondOfDay() / 60; m + slotMinutes <= blockEnd; m += slotMinutes) {
				LocalDateTime start = dayStart.plusMinutes(m);
				LocalDateTime end = start.plusMinutes(slotMinutes);
				boolean overlaps = busy.stream().anyMatch(b -> start.isBefore(b[1]) && end.isAfter(b[0]));
				if (start.isAfter(now) && !overlaps) {
					slots.add(new SlotResponse(start, end));
				}
			}
		}
		return slots;
	}

	TrainerProfile requireActive(Long trainerId) {
		return trainerRepository.findByIdAndActiveTrue(trainerId)
				.orElseThrow(() -> ApiException.notFound("TRAINER_NOT_FOUND", "Không tìm thấy huấn luyện viên"));
	}

	/**
	 * HLV có đang bận (lịch PT hoặc dạy lớp) trong khoảng [from, to) không.
	 */
	boolean isBusy(Long trainerId, LocalDateTime from, LocalDateTime to) {
		return !bookingRepository.findActivePtOfTrainer(trainerId, from, to).isEmpty()
				|| !classSessionRepository.findScheduledOfTrainer(trainerId, from, to).isEmpty();
	}

	private List<ScheduleItem> scheduleOf(Long trainerId) {
		return scheduleRepository.findByTrainerIdOrderByDayOfWeekAscStartTimeAsc(trainerId).stream()
				.map(mapper::toItem)
				.toList();
	}

	private static void validateSchedule(List<ScheduleItem> items) {
		List<ScheduleItem> sorted = items.stream()
				.sorted(Comparator.comparing(ScheduleItem::dayOfWeek).thenComparing(ScheduleItem::startTime))
				.toList();
		ScheduleItem prev = null;
		for (ScheduleItem item : sorted) {
			if (!item.startTime().isBefore(item.endTime())) {
				throw ApiException.badRequest("SCHEDULE_INVALID_TIME",
						"Ca làm việc thứ %d: giờ bắt đầu phải trước giờ kết thúc".formatted(item.dayOfWeek()));
			}
			if (prev != null && prev.dayOfWeek().equals(item.dayOfWeek()) && item.startTime().isBefore(prev.endTime())) {
				throw ApiException.badRequest("SCHEDULE_OVERLAP",
						"Ca làm việc thứ %d bị chồng giờ nhau".formatted(item.dayOfWeek()));
			}
			prev = item;
		}
	}
}
