package com.duyanh.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.otp.OtpSentResponse;
import com.duyanh.dto.otp.SendOtpRequest;
import com.duyanh.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "OTP")
@SecurityRequirements
@RestController
@RequestMapping("/api/otp")
@RequiredArgsConstructor
public class OtpController {

	private final AuthService authService;

	@Operation(summary = "Gửi lại mã OTP (chờ 60 giây giữa 2 lần, tối đa 5 lần / giờ)")
	@PostMapping("/send")
	public OtpSentResponse send(@Valid @RequestBody SendOtpRequest req) {
		return authService.requestOtp(req.email(), req.purpose());
	}
}
