package com.duyanh.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.duyanh.entity.enums.SubscriptionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gói hội viên đã mua. Tạo ở trạng thái PENDING, chuyển ACTIVE khi Payment SUCCESS.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subscriptions")
public class Subscription extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "package_id", nullable = false)
	private MembershipPackage membershipPackage;

	// NULL khi còn PENDING
	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SubscriptionStatus status = SubscriptionStatus.PENDING;

	// Giá tại thời điểm mua, không đổi theo giá gói sau này
	@Column(name = "price_paid", nullable = false, precision = 15, scale = 2)
	private BigDecimal pricePaid;

	@Column(name = "pt_sessions_remaining", nullable = false)
	private Integer ptSessionsRemaining = 0;

	@Column(name = "frozen_from")
	private LocalDate frozenFrom;

	@Column(name = "frozen_until")
	private LocalDate frozenUntil;

	@Column(name = "frozen_days_used", nullable = false)
	private Integer frozenDaysUsed = 0;

	@Version
	@Column(nullable = false)
	private Integer version;

	public boolean isUsableOn(LocalDate date) {
		return status == SubscriptionStatus.ACTIVE
				&& startDate != null && !date.isBefore(startDate)
				&& endDate != null && !date.isAfter(endDate);
	}
}
