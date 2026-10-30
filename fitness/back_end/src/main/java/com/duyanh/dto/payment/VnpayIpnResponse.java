package com.duyanh.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body trả về cho VNPay khi nhận IPN, đúng định dạng VNPay yêu cầu: {"RspCode":"00","Message":"..."}.
 */
public record VnpayIpnResponse(
		@JsonProperty("RspCode") String rspCode,
		@JsonProperty("Message") String message) {
}
