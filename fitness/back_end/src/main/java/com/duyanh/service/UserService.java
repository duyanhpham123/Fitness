package com.duyanh.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.audit.AuditActions;
import com.duyanh.audit.Auditable;
import com.duyanh.dto.user.ChangePasswordRequest;
import com.duyanh.dto.user.UpdateProfileRequest;
import com.duyanh.dto.user.UserResponse;
import com.duyanh.entity.User;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.UserMapper;
import com.duyanh.repository.RefreshTokenRepository;
import com.duyanh.repository.UserRepository;
import com.duyanh.security.TokenBlacklistService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final TokenBlacklistService blacklistService;
	private final PasswordEncoder passwordEncoder;
	private final UserMapper userMapper;

	@Transactional(readOnly = true)
	public UserResponse getProfile(Long userId) {
		return userMapper.toResponse(findUser(userId));
	}

	@Auditable(action = AuditActions.PROFILE_UPDATE, entityType = "USER", entityId = "#userId")
	@Transactional
	public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
		User user = findUser(userId);
		String phone = blankToNull(req.phone());
		if (phone != null && userRepository.existsByPhoneAndIdNot(phone, userId)) {
			throw ApiException.conflict("PHONE_EXISTS", "Số điện thoại đã được sử dụng");
		}
		user.setFullName(req.fullName().trim());
		user.setPhone(phone);
		user.setGender(req.gender());
		user.setDateOfBirth(req.dateOfBirth());
		user.setAddress(blankToNull(req.address()));
		user.setAvatarUrl(blankToNull(req.avatarUrl()));
		return userMapper.toResponse(user);
	}

	/**
	 * Đổi mật khẩu khi đang đăng nhập. Sau khi đổi: đăng xuất khỏi mọi thiết bị (kể cả thiết bị
	 * hiện tại), người dùng đăng nhập lại bằng mật khẩu mới.
	 */
	@Auditable(action = AuditActions.PASSWORD_CHANGE, entityType = "USER", entityId = "#userId")
	@Transactional
	public void changePassword(Long userId, ChangePasswordRequest req) {
		User user = findUser(userId);
		if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
			throw ApiException.badRequest("WRONG_PASSWORD", "Mật khẩu hiện tại không đúng");
		}
		if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
			throw ApiException.badRequest("SAME_PASSWORD", "Mật khẩu mới phải khác mật khẩu hiện tại");
		}
		user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
		refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
		blacklistService.revokeAllForUser(userId);
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Không tìm thấy người dùng"));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
