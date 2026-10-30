package com.duyanh.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.user.ChangePasswordRequest;
import com.duyanh.dto.user.UpdateProfileRequest;
import com.duyanh.dto.user.UserResponse;
import com.duyanh.security.UserPrincipal;
import com.duyanh.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "User")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@Operation(summary = "Thông tin người dùng đang đăng nhập")
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
		return userService.getProfile(principal.getId());
	}

	@Operation(summary = "Sửa hồ sơ cá nhân")
	@PutMapping("/me")
	public UserResponse updateMe(@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody UpdateProfileRequest req) {
		return userService.updateProfile(principal.getId(), req);
	}

	@Operation(summary = "Đổi mật khẩu; đăng xuất khỏi mọi thiết bị, cần đăng nhập lại")
	@PutMapping("/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody ChangePasswordRequest req) {
		userService.changePassword(principal.getId(), req);
	}
}
