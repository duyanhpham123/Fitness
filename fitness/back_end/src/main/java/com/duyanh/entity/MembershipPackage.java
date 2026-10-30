package com.duyanh.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gói tập mẫu (1 tháng, 3 tháng, PT 1-1...).
 * Đặt tên MembershipPackage để không trùng với java.lang.Package.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "packages")
public class MembershipPackage extends BaseEntity {

	@Column(nullable = false, length = 30, unique = true)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 500)
	private String description;

	@Column(name = "duration_days", nullable = false)
	private Integer durationDays;

	@Column(nullable = false, precision = 15, scale = 2)
	private BigDecimal price;

	// Số buổi PT miễn phí đi kèm
	@Column(name = "pt_sessions", nullable = false)
	private Integer ptSessions = 0;

	@Column(name = "max_freeze_days", nullable = false)
	private Integer maxFreezeDays = 0;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;
}
