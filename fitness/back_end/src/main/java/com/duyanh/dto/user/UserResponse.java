package com.duyanh.dto.user;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import com.duyanh.entity.enums.Gender;
import com.duyanh.entity.enums.UserStatus;

public record UserResponse(
		Long id,
		String email,
		String phone,
		String fullName,
		Gender gender,
		LocalDate dateOfBirth,
		String avatarUrl,
		String address,
		UserStatus status,
		Set<String> roles,
		LocalDateTime createdAt) {
}
