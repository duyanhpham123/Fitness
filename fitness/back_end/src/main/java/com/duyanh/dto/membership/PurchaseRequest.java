package com.duyanh.dto.membership;

import com.duyanh.entity.enums.PaymentMethod;

import jakarta.validation.constraints.NotNull;

/**
 * Mua gói mới hoặc gia hạn. Nếu đang có gói còn hạn, gói mới bắt đầu ngay sau ngày kết thúc của gói đó.
 */
public record PurchaseRequest(

		@NotNull(message = "Chưa chọn gói tập")
		Long packageId,

		@NotNull(message = "Chưa chọn phương thức thanh toán")
		PaymentMethod method) {
}
