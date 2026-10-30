package com.duyanh.exception;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Format lỗi chung cho toàn bộ API.
 *
 * <pre>
 * {
 *   "timestamp": "2026-10-29T08:00:00Z",
 *   "status": 400,
 *   "code": "VALIDATION_ERROR",
 *   "message": "Dữ liệu không hợp lệ",
 *   "path": "/api/auth/register",
 *   "errors": { "email": "Email không hợp lệ" }
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
		Instant timestamp,
		int status,
		String code,
		String message,
		String path,
		Map<String, String> errors) {

	public static ErrorResponse of(int status, String code, String message, String path) {
		return new ErrorResponse(Instant.now(), status, code, message, path, null);
	}
}
