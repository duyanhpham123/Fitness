package com.duyanh.dto.checkin;

/**
 * Mã để app hội viên vẽ thành QR. Mỗi mã chỉ dùng 1 lần.
 *
 * @param expiresIn số giây mã còn hiệu lực
 */
public record QrCodeResponse(String qrToken, long expiresIn) {
}
