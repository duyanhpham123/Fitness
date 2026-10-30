package com.duyanh.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.duyanh.entity.enums.NotificationChannel;
import com.duyanh.entity.enums.NotificationType;

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

/**
 * Thông báo cho từng user. Gửi thông báo chung (broadcast) = tạo 1 dòng cho mỗi user.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(nullable = false, length = 1000)
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationChannel channel = NotificationChannel.IN_APP;

	// Đối tượng liên quan, ví dụ BOOKING + id để mở chi tiết
	@Column(name = "reference_type", length = 30)
	private String referenceType;

	@Column(name = "reference_id")
	private Long referenceId;

	@Column(name = "is_read", nullable = false)
	private boolean read = false;

	@Column(name = "read_at")
	private LocalDateTime readAt;

	// Thời điểm gửi email/SMS thành công
	@Column(name = "sent_at")
	private LocalDateTime sentAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
