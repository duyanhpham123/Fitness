package com.duyanh.dto.fitnessclass;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClassRequest(

		@NotBlank(message = "Tên lớp không được để trống")
		@Size(max = 100, message = "Tên lớp tối đa 100 ký tự")
		String name,

		@Size(max = 500, message = "Mô tả tối đa 500 ký tự")
		String description,

		@NotNull(message = "Thiếu thời lượng")
		@Min(value = 15, message = "Thời lượng tối thiểu 15 phút")
		@Max(value = 240, message = "Thời lượng tối đa 240 phút")
		Integer durationMinutes,

		@NotNull(message = "Thiếu sức chứa mặc định")
		@Min(value = 1, message = "Sức chứa tối thiểu 1")
		@Max(value = 500, message = "Sức chứa tối đa 500")
		Integer defaultCapacity,

		@Size(max = 500, message = "Link ảnh tối đa 500 ký tự")
		String imageUrl) {
}
