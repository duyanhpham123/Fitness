package com.duyanh.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.auth.AuthResponse;
import com.duyanh.dto.auth.ForgotPasswordRequest;
import com.duyanh.dto.auth.LoginRequest;
import com.duyanh.dto.auth.RefreshTokenRequest;
import com.duyanh.dto.auth.RegisterRequest;
import com.duyanh.dto.auth.RegisterResponse;
import com.duyanh.dto.auth.ResetPasswordRequest;
import com.duyanh.dto.auth.VerifyEmailRequest;
import com.duyanh.dto.otp.OtpSentResponse;
import com.duyanh.entity.enums.OtpPurpose;
import com.duyanh.exception.ApiException;
import com.duyanh.security.JwtAuthenticationFilter;
import com.duyanh.security.RefreshTokenCookie;
import com.duyanh.service.AuthService;
import com.duyanh.service.AuthService.ClientInfo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Authentication")
@SecurityRequirements // các API này không cần token
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final RefreshTokenCookie cookie;

	@Operation(summary = "Đăng ký tài khoản hội viên (trạng thái PENDING, gửi OTP xác thực email)")
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public RegisterResponse register(@Valid @RequestBody RegisterRequest req) {
		return authService.register(req);
	}

	@Operation(summary = "Xác thực email bằng OTP, kích hoạt tài khoản và đăng nhập luôn")
	@PostMapping("/verify-email")
	public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest req, HttpServletRequest http,
			HttpServletResponse response) {
		return cookie.write(response, authService.verifyEmail(req, clientInfo(http)));
	}

	@Operation(summary = "Đăng nhập: access token trong body, refresh token trong cookie HttpOnly (dev: cả trong body)")
	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http,
			HttpServletResponse response) {
		return cookie.write(response, authService.login(req, clientInfo(http)));
	}

	@Operation(summary = "Đổi refresh token (cookie hoặc body) lấy cặp token mới; refresh token cũ bị thu hồi")
	@PostMapping("/refresh")
	public AuthResponse refresh(@RequestBody(required = false) RefreshTokenRequest req, HttpServletRequest http,
			HttpServletResponse response) {
		String token = cookie.resolve(http, req == null ? null : req.refreshToken());
		if (token == null) {
			throw ApiException.unauthorized("REFRESH_TOKEN_INVALID", "Refresh token không hợp lệ");
		}
		return cookie.write(response, authService.refresh(token, clientInfo(http)));
	}

	@Operation(summary = "Đăng xuất: thu hồi refresh token (cookie hoặc body), xóa cookie; gửi kèm Authorization để chặn luôn access token")
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@RequestBody(required = false) RefreshTokenRequest req, HttpServletRequest http,
			HttpServletResponse response) {
		String token = cookie.resolve(http, req == null ? null : req.refreshToken());
		if (token != null) {
			authService.logout(token, JwtAuthenticationFilter.extractBearerToken(http));
		}
		cookie.clear(response);
	}

	@Operation(summary = "Quên mật khẩu: gửi OTP đặt lại mật khẩu tới email")
	@PostMapping("/forgot-password")
	public OtpSentResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
		return authService.requestOtp(req.email(), OtpPurpose.RESET_PASSWORD);
	}

	@Operation(summary = "Đặt lại mật khẩu bằng OTP (đăng xuất khỏi mọi thiết bị)")
	@PostMapping("/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
		authService.resetPassword(req);
	}

	private static ClientInfo clientInfo(HttpServletRequest http) {
		return new ClientInfo(http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
	}
}
