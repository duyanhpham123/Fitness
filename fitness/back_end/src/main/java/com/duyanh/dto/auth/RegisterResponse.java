package com.duyanh.dto.auth;

import com.duyanh.dto.otp.OtpSentResponse;
import com.duyanh.dto.user.UserResponse;

/**
 * Tài khoản mới ở trạng thái PENDING, cần gọi /api/auth/verify-email với mã OTP để kích hoạt.
 */
public record RegisterResponse(UserResponse user, OtpSentResponse otp) {
}
