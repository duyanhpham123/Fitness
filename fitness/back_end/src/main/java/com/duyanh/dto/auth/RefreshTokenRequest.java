package com.duyanh.dto.auth;

/**
 * Dùng cho /api/auth/refresh và /api/auth/logout.
 *
 * @param refreshToken không bắt buộc: trình duyệt gửi qua cookie HttpOnly; client không dùng cookie
 *                     (app di động, Swagger) thì gửi trong body
 */
public record RefreshTokenRequest(String refreshToken) {
}
