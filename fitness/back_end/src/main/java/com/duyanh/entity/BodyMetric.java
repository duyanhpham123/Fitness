package com.duyanh.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Chỉ số cơ thể theo ngày, dùng để theo dõi tiến độ. Mỗi user tối đa 1 dòng / ngày.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "body_metrics")
public class BodyMetric {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "measured_at", nullable = false)
	private LocalDate measuredAt;

	@Column(name = "weight_kg", precision = 5, scale = 1)
	private BigDecimal weightKg;

	@Column(name = "height_cm", precision = 5, scale = 1)
	private BigDecimal heightCm;

	@Column(name = "body_fat_pct", precision = 4, scale = 1)
	private BigDecimal bodyFatPct;

	@Column(name = "muscle_mass_kg", precision = 5, scale = 1)
	private BigDecimal muscleMassKg;

	@Column(length = 255)
	private String note;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
