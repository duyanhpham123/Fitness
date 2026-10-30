package com.duyanh.entity.enums;

public enum OtpPurpose {
	VERIFY_EMAIL("Xác thực email"),
	RESET_PASSWORD("Đặt lại mật khẩu");

	private final String label;

	OtpPurpose(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
