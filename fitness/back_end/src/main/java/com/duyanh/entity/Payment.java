package com.duyanh.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.duyanh.entity.enums.PaymentMethod;
import com.duyanh.entity.enums.PaymentStatus;

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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

	// Mã đơn gửi sang cổng thanh toán (vnp_TxnRef)
	@Column(nullable = false, length = 40, unique = true)
	private String code;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subscription_id")
	private Subscription subscription;

	@Column(nullable = false, precision = 15, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentMethod method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status = PaymentStatus.PENDING;

	@Column(name = "idempotency_key", length = 64)
	private String idempotencyKey;

	// Mã giao dịch phía cổng (vnp_TransactionNo), unique cùng method
	@Column(name = "gateway_txn_id", length = 64)
	private String gatewayTxnId;

	@Column(name = "gateway_response_code", length = 10)
	private String gatewayResponseCode;

	// Lưu nguyên dữ liệu IPN để đối soát
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_callback")
	private Map<String, Object> rawCallback;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	@Version
	@Column(nullable = false)
	private Integer version;
}
