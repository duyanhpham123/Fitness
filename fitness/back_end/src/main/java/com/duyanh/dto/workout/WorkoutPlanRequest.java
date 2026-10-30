package com.duyanh.dto.workout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Tạo / thay toàn bộ nội dung kế hoạch tập.
 *
 * @param memberId bắt buộc khi HLV soạn cho hội viên; hội viên tự soạn thì bỏ trống
 */
public record WorkoutPlanRequest(

		Long memberId,

		@NotBlank(message = "Tên kế hoạch không được để trống")
		@Size(max = 150, message = "Tên kế hoạch tối đa 150 ký tự")
		String name,

		@Size(max = 255, message = "Mục tiêu tối đa 255 ký tự")
		String goal,

		LocalDate startDate,

		LocalDate endDate,

		@NotEmpty(message = "Kế hoạch phải có ít nhất 1 ngày tập")
		@Size(max = 60, message = "Tối đa 60 ngày tập")
		List<@Valid Day> days) {

	public record Day(

			@NotNull(message = "Thiếu số thứ tự ngày")
			@Min(value = 1, message = "Ngày tập bắt đầu từ 1")
			Integer dayNumber,

			@NotBlank(message = "Tên ngày tập không được để trống")
			@Size(max = 150, message = "Tên ngày tập tối đa 150 ký tự")
			String title,

			@NotEmpty(message = "Mỗi ngày tập phải có ít nhất 1 bài tập")
			@Size(max = 30, message = "Tối đa 30 bài tập mỗi ngày")
			List<@Valid Item> exercises) {
	}

	/**
	 * Bài tính theo số lần (sets + reps) hoặc theo thời gian (sets + durationSeconds, vd. plank).
	 */
	public record Item(

			@NotNull(message = "Chưa chọn bài tập")
			Long exerciseId,

			@Min(value = 1, message = "Số hiệp tối thiểu 1")
			@Max(value = 20, message = "Số hiệp tối đa 20")
			Integer sets,

			@Min(value = 1, message = "Số lần tối thiểu 1")
			@Max(value = 500, message = "Số lần tối đa 500")
			Integer reps,

			@Min(value = 1, message = "Thời gian tối thiểu 1 giây")
			@Max(value = 7200, message = "Thời gian tối đa 7200 giây")
			Integer durationSeconds,

			@Min(value = 0, message = "Thời gian nghỉ không được âm")
			@Max(value = 600, message = "Thời gian nghỉ tối đa 600 giây")
			Integer restSeconds,

			@DecimalMin(value = "0", message = "Mức tạ không được âm")
			@DecimalMax(value = "1000", message = "Mức tạ tối đa 1000 kg")
			BigDecimal weightKg,

			@Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
			String note) {
	}
}
