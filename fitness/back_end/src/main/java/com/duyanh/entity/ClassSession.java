package com.duyanh.entity;

import java.time.LocalDateTime;

import com.duyanh.entity.enums.ClassSessionStatus;

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
 * Một buổi học cụ thể. booked_count chỉ thay đổi qua
 * ClassSessionRepository.tryReserveSeat / releaseSeat (update nguyên tử).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "class_sessions")
public class ClassSession extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "class_id", nullable = false)
	private FitnessClass fitnessClass;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trainer_id", nullable = false)
	private TrainerProfile trainer;

	@Column(length = 50)
	private String room;

	@Column(name = "start_at", nullable = false)
	private LocalDateTime startAt;

	@Column(name = "end_at", nullable = false)
	private LocalDateTime endAt;

	@Column(nullable = false)
	private Integer capacity;

	@Column(name = "booked_count", nullable = false)
	private Integer bookedCount = 0;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ClassSessionStatus status = ClassSessionStatus.SCHEDULED;

	public int getAvailableSeats() {
		return capacity - bookedCount;
	}
}
