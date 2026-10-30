package com.duyanh.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.duyanh.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Trả 401 dạng JSON khi gọi endpoint cần đăng nhập mà không có token hợp lệ.
 * code = TOKEN_EXPIRED -> frontend gọi /api/auth/refresh rồi thử lại.
 * code = TOKEN_REVOKED -> frontend chuyển về màn đăng nhập.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		Object jwtError = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
		String code = jwtError != null ? jwtError.toString() : "UNAUTHORIZED";
		String message = switch (code) {
			case "TOKEN_EXPIRED" -> "Phiên đăng nhập đã hết hạn";
			case "TOKEN_INVALID" -> "Token không hợp lệ";
			case "TOKEN_REVOKED" -> "Phiên đăng nhập đã bị thu hồi, vui lòng đăng nhập lại";
			default -> "Bạn cần đăng nhập";
		};
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(),
				ErrorResponse.of(401, code, message, request.getRequestURI()));
	}
}
