package com.duyanh.dto.otp;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param expiresIn   số giây mã OTP còn hiệu lực
 * @param resendAfter số giây phải chờ trước khi được gửi lại
 * @param devOtp      chỉ có khi app.otp.expose-in-response=true (môi trường dev)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpSentResponse(
		String message,
		long expiresIn,
		long resendAfter,
		String devOtp) {
}
