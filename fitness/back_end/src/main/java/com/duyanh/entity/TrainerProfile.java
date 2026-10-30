package com.duyanh.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Hồ sơ HLV, quan hệ 1-1 với User: khóa chính chính là user_id.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "trainer_profiles")
public class TrainerProfile {

	@Id
	@Column(name = "user_id")
	private Long id;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(length = 150)
	private String specialization;

	@Column(columnDefinition = "TEXT")
	private String bio;

	@Column(name = "experience_years", nullable = false)
	private Integer experienceYears = 0;

	@Column(length = 500)
	private String certifications;

	@Column(name = "session_price", precision = 15, scale = 2)
	private BigDecimal sessionPrice;

	@Column(nullable = false, precision = 2, scale = 1)
	private BigDecimal rating = BigDecimal.ZERO;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}
