package com.duyanh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lớp học nhóm (Yoga, Zumba...). Đặt tên FitnessClass vì "Class" trùng với java.lang.Class.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "classes")
public class FitnessClass extends BaseEntity {

	@Column(nullable = false, length = 100, unique = true)
	private String name;

	@Column(length = 500)
	private String description;

	@Column(name = "duration_minutes", nullable = false)
	private Integer durationMinutes = 60;

	@Column(name = "default_capacity", nullable = false)
	private Integer defaultCapacity = 20;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;
}
