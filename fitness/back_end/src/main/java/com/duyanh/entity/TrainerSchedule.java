package com.duyanh.entity;

import java.time.LocalTime;

import com.duyanh.entity.converter.WallClockTimeConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * Lịch làm việc lặp lại hàng tuần của HLV.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "trainer_schedules")
public class TrainerSchedule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trainer_id", nullable = false)
	private TrainerProfile trainer;

	// 1 = Thứ 2 ... 7 = Chủ nhật, khớp với java.time.DayOfWeek.getValue()
	@Column(name = "day_of_week", nullable = false)
	private Integer dayOfWeek;

	// Giờ treo tường, không đổi múi giờ (xem WallClockTimeConverter)
	@Convert(converter = WallClockTimeConverter.class)
	@Column(name = "start_time", nullable = false, columnDefinition = "TIME")
	private LocalTime startTime;

	@Convert(converter = WallClockTimeConverter.class)
	@Column(name = "end_time", nullable = false, columnDefinition = "TIME")
	private LocalTime endTime;
}
