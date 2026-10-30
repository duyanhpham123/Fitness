package com.duyanh.entity;

import java.time.LocalDateTime;

import com.duyanh.entity.enums.CheckInMethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "check_ins")
public class CheckIn {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subscription_id", nullable = false)
	private Subscription subscription;

	@Column(name = "checked_in_at", nullable = false)
	private LocalDateTime checkedInAt = LocalDateTime.now();

	@Column(name = "checked_out_at")
	private LocalDateTime checkedOutAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CheckInMethod method = CheckInMethod.QR;

	// Lễ tân check-in thủ công
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "staff_id")
	private User staff;
}
