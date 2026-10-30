package com.duyanh.dto.workout;

import com.duyanh.entity.enums.MuscleGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExerciseRequest(

		@NotBlank(message = "Tên bài tập không được để trống")
		@Size(max = 100, message = "Tên bài tập tối đa 100 ký tự")
		String name,

		@NotNull(message = "Chưa chọn nhóm cơ")
		MuscleGroup muscleGroup,

		@Size(max = 100, message = "Dụng cụ tối đa 100 ký tự")
		String equipment,

		@Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
		String description,

		@Size(max = 500, message = "Link ảnh tối đa 500 ký tự")
		String imageUrl,

		@Size(max = 500, message = "Link video tối đa 500 ký tự")
		String videoUrl) {
}
