package com.duyanh.dto.membership;

import com.duyanh.dto.payment.PaymentResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param payment null khi gói miễn phí (kích hoạt ngay, không cần thanh toán)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PurchaseResponse(SubscriptionResponse subscription, PaymentResponse payment) {
}
