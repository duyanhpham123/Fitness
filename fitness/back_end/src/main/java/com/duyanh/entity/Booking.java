package com.duyanh.entity;

import java.time.LocalDateTime;

import com.duyanh.entity.enums.BookingStatus;
import com.duyanh.entity.enums.BookingType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Đặt lịch PT 1-1 (trainer bắt buộc) hoặc đặt lớp (classSession bắt buộc).
 *
 * Hai cột sinh tự động active_pt_slot / active_class_session trong DB không được map:
 * DB tự tính và dùng cho unique key chống đặt trùng. Khi insert trùng, DB ném lỗi
 * DataIntegrityViolationException -> service bắt lỗi và trả về 409.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "bookings")
public class Booking extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private User member;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subscription_id", nullable = false)
	private Subscription subscription;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BookingType type;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "trainer_id")
	private TrainerProfile trainer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "class_session_id")
	private ClassSession classSession;

	@Column(name = "start_at", nullable = false)
	private LocalDateTime startAt;

	@Column(name = "end_at", nullable = false)
	private LocalDateTime endAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BookingStatus status = BookingStatus.CONFIRMED;

	@Column(length = 255)
	private String note;

	@Column(name = "idempotency_key", length = 64)
	private String idempotencyKey;

	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;

	@Column(name = "cancel_reason", length = 255)
	private String cancelReason;
}
