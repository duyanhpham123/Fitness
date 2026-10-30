package com.duyanh.dto.trainer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HLV tự sửa hồ sơ. Giá buổi PT và điểm đánh giá do phòng tập quản lý, không sửa ở đây.
 */
public record TrainerProfileRequest(

		@Size(max = 150, message = "Chuyên môn tối đa 150 ký tự")
		String specialization,

		@Size(max = 2000, message = "Giới thiệu tối đa 2000 ký tự")
		String bio,

		@NotNull(message = "Thiếu số năm kinh nghiệm")
		@Min(value = 0, message = "Số năm kinh nghiệm không được âm")
		@Max(value = 60, message = "Số năm kinh nghiệm tối đa 60")
		Integer experienceYears,

		@Size(max = 500, message = "Chứng chỉ tối đa 500 ký tự")
		String certifications) {
}
