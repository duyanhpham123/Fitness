package com.duyanh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.audit.AuditActions;
import com.duyanh.audit.Auditable;
import com.duyanh.dto.fitnessclass.ClassRequest;
import com.duyanh.dto.fitnessclass.ClassResponse;
import com.duyanh.dto.fitnessclass.ClassSessionRequest;
import com.duyanh.dto.fitnessclass.ClassSessionResponse;
import com.duyanh.entity.Booking;
import com.duyanh.entity.ClassSession;
import com.duyanh.entity.FitnessClass;
import com.duyanh.entity.TrainerProfile;
import com.duyanh.entity.enums.BookingStatus;
import com.duyanh.entity.enums.ClassSessionStatus;
import com.duyanh.event.BookingChangedEvent;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.ClassMapper;
import com.duyanh.repository.BookingRepository;
import com.duyanh.repository.ClassSessionRepository;
import com.duyanh.repository.FitnessClassRepository;

import lombok.RequiredArgsConstructor;

/**
 * Lớp học nhóm (Yoga, Zumba...) và các buổi học cụ thể.
 */
@Service
@RequiredArgsConstructor
public class ClassService {

	private static final List<BookingStatus> ACTIVE_BOOKINGS = List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

	private final FitnessClassRepository classRepository;
	private final ClassSessionRepository sessionRepository;
	private final BookingRepository bookingRepository;
	private final TrainerService trainerService;
	private final ApplicationEventPublisher eventPublisher;
	private final ClassMapper mapper;

	/**
	 * Hủy buổi học chưa diễn ra: hủy luôn mọi lịch đã đặt và báo cho từng hội viên (kèm lý do).
	 */
	@Auditable(action = AuditActions.CLASS_SESSION_CANCEL, entityType = "CLASS_SESSION", entityId = "#sessionId",
			details = "{'reason': #reason}")
	@Transactional
	public ClassSessionResponse cancelSession(Long sessionId, String reason) {
		ClassSession session = sessionRepository.findWithDetailsById(sessionId)
				.orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "Không tìm thấy buổi học"));
		if (session.getStatus() != ClassSessionStatus.SCHEDULED) {
			throw ApiException.conflict("SESSION_NOT_CANCELLABLE", "Buổi học đã bị hủy hoặc đã kết thúc");
		}
		if (!session.getStartAt().isAfter(LocalDateTime.now())) {
			throw ApiException.badRequest("SESSION_ALREADY_STARTED", "Buổi học đã bắt đầu, không thể hủy");
		}

		String cleanReason = reason.trim();
		String target = "lớp " + session.getFitnessClass().getName();
		for (Booking b : bookingRepository.findByClassSessionIdAndStatusIn(sessionId, ACTIVE_BOOKINGS)) {
			b.setStatus(BookingStatus.CANCELLED);
			b.setCancelledAt(LocalDateTime.now());
			b.setCancelReason("Phòng tập hủy buổi học: " + cleanReason);
			eventPublisher.publishEvent(new BookingChangedEvent(b.getId(), b.getMember().getId(), target,
					b.getStartAt(), true, cleanReason));
		}
		session.setStatus(ClassSessionStatus.CANCELLED);
		session.setBookedCount(0);
		return mapper.toResponse(session);
	}

	@Transactional(readOnly = true)
	public List<ClassResponse> listClasses() {
		return classRepository.findByActiveTrueOrderByNameAsc().stream().map(mapper::toResponse).toList();
	}

	@Auditable(action = AuditActions.CLASS_CREATE, entityType = "CLASS", entityId = "#result.id()", details = "#req")
	@Transactional
	public ClassResponse createClass(ClassRequest req) {
		String name = req.name().trim();
		if (classRepository.existsByName(name)) {
			throw ApiException.conflict("CLASS_NAME_EXISTS", "Tên lớp đã tồn tại");
		}
		FitnessClass c = new FitnessClass();
		c.setName(name);
		c.setDescription(req.description());
		c.setDurationMinutes(req.durationMinutes());
		c.setDefaultCapacity(req.defaultCapacity());
		c.setImageUrl(req.imageUrl());
		return mapper.toResponse(classRepository.save(c));
	}

	@Transactional(readOnly = true)
	public List<ClassSessionResponse> listSessions(LocalDate date) {
		LocalDateTime from = date.atStartOfDay();
		return sessionRepository.findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(from, from.plusDays(1))
				.stream().map(mapper::toResponse).toList();
	}

	/**
	 * Mở buổi học. HLV không được trùng giờ với lịch PT hoặc buổi lớp khác.
	 */
	@Auditable(action = AuditActions.CLASS_SESSION_CREATE, entityType = "CLASS_SESSION", entityId = "#result.id()",
			details = "#req")
	@Transactional
	public ClassSessionResponse createSession(ClassSessionRequest req) {
		FitnessClass fitnessClass = classRepository.findById(req.classId())
				.filter(FitnessClass::isActive)
				.orElseThrow(() -> ApiException.badRequest("CLASS_NOT_AVAILABLE", "Lớp học không tồn tại hoặc đã ngừng"));
		TrainerProfile trainer = trainerService.requireActive(req.trainerId());

		LocalDateTime start = req.startAt();
		LocalDateTime end = start.plusMinutes(fitnessClass.getDurationMinutes());
		if (trainerService.isBusy(trainer.getId(), start, end)) {
			throw ApiException.conflict("TRAINER_BUSY", "Huấn luyện viên đã có lịch trong khung giờ này");
		}

		ClassSession session = new ClassSession();
		session.setFitnessClass(fitnessClass);
		session.setTrainer(trainer);
		session.setRoom(req.room());
		session.setStartAt(start);
		session.setEndAt(end);
		session.setCapacity(req.capacity() != null ? req.capacity() : fitnessClass.getDefaultCapacity());
		return mapper.toResponse(sessionRepository.save(session));
	}
}
