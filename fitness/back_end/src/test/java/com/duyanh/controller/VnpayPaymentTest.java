package com.duyanh.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.duyanh.service.PaymentService;
import com.duyanh.service.VnpayService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Giả lập VNPay: test tự ký tham số IPN bằng hashSecret dành riêng cho test.
 * Payment id 4 trong dữ liệu mẫu là đơn VNPAY đang PENDING của member3.
 */
@TestPropertySource(properties = {
		"app.vnpay.tmn-code=TESTTMN1",
		"app.vnpay.hash-secret=test-hash-secret-chi-dung-trong-test"
})
class VnpayPaymentTest extends ApiTestSupport {

	private static final long GYM_1M = 1;
	private static final String GYM_1M_VNP_AMOUNT = "120000000"; // 1.200.000đ x 100

	@Autowired
	VnpayService vnpayService;

	@Autowired
	PaymentService paymentService;

	@Test
	void createPaymentUrl_isSignedAndOnlyForOwner() throws Exception {
		JsonNode payment = purchaseVnpay(MEMBER3);
		long id = payment.get("id").asLong();

		String url = readJson(postAs(MEMBER3, "/api/payments/" + id + "/vnpay-url", "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.expiresAt").isNotEmpty())).get("paymentUrl").asText();

		Map<String, String> params = new HashMap<>();
		UriComponentsBuilder.fromUriString(url).build().getQueryParams()
				.forEach((k, v) -> params.put(k, URLDecoder.decode(v.get(0), StandardCharsets.US_ASCII)));
		assertTrue(url.startsWith("https://sandbox.vnpayment.vn/"));
		assertEquals(GYM_1M_VNP_AMOUNT, params.get("vnp_Amount"));
		assertEquals(payment.get("code").asText(), params.get("vnp_TxnRef"));
		assertEquals("TESTTMN1", params.get("vnp_TmnCode"));
		assertTrue(vnpayService.verifySignature(params), "Chữ ký trong URL phải hợp lệ");

		postAs(MEMBER1, "/api/payments/" + id + "/vnpay-url", "")
				.andExpect(status().isNotFound());
	}

	@Test
	void createPaymentUrl_forCashPayment_isRejected() throws Exception {
		long id = readJson(purchase(MEMBER3, "CASH")).at("/payment/id").asLong();
		postAs(MEMBER3, "/api/payments/" + id + "/vnpay-url", "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PAYMENT_METHOD_NOT_VNPAY"));
	}

	@Test
	void ipnSuccess_activatesSubscription_andRepeatIsIgnored() throws Exception {
		JsonNode payment = purchaseVnpay(MEMBER3);
		Map<String, String> ipn = signedIpn(payment.get("code").asText(), GYM_1M_VNP_AMOUNT, "00");

		ipn(ipn).andExpect(status().isOk())
				.andExpect(jsonPath("$.RspCode").value("00"));

		getAs(MEMBER3, "/api/payments/" + payment.get("id").asLong())
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.paidAt").isNotEmpty());
		getAs(MEMBER3, "/api/subscriptions/me")
				.andExpect(jsonPath("$[?(@.id == %d)].status".formatted(payment.get("subscriptionId").asLong()))
						.value("ACTIVE"));

		// VNPay gọi lại IPN -> không xử lý lần 2
		ipn(ipn).andExpect(jsonPath("$.RspCode").value("02"));
	}

	@Test
	void ipnFailure_marksPaymentFailed_andCancelsSubscription() throws Exception {
		JsonNode payment = purchaseVnpay(MEMBER3);
		// 24 = khách hàng hủy giao dịch
		ipn(signedIpn(payment.get("code").asText(), GYM_1M_VNP_AMOUNT, "24"))
				.andExpect(jsonPath("$.RspCode").value("00"));

		getAs(MEMBER3, "/api/payments/" + payment.get("id").asLong())
				.andExpect(jsonPath("$.status").value("FAILED"));
		getAs(MEMBER3, "/api/subscriptions/me")
				.andExpect(jsonPath("$[?(@.id == %d)].status".formatted(payment.get("subscriptionId").asLong()))
						.value("CANCELLED"));
	}

	@Test
	void ipn_rejectsBadSignature_wrongAmount_unknownOrder() throws Exception {
		String code = purchaseVnpay(MEMBER3).get("code").asText();

		// Sửa số tiền sau khi ký -> chữ ký sai
		Map<String, String> tampered = signedIpn(code, GYM_1M_VNP_AMOUNT, "00");
		tampered.put("vnp_Amount", "100");
		ipn(tampered).andExpect(jsonPath("$.RspCode").value("97"));

		// Ký đúng nhưng số tiền khác đơn
		ipn(signedIpn(code, "100", "00")).andExpect(jsonPath("$.RspCode").value("04"));

		ipn(signedIpn("PAY_KHONG_TON_TAI", GYM_1M_VNP_AMOUNT, "00")).andExpect(jsonPath("$.RspCode").value("01"));
	}

	@Test
	void returnUrl_processesResult_andRedirectsToFrontend() throws Exception {
		String code = purchaseVnpay(MEMBER3).get("code").asText();
		MockHttpServletRequestBuilder req = get("/api/payments/vnpay/return");
		signedIpn(code, GYM_1M_VNP_AMOUNT, "00").forEach(req::param);

		mvc.perform(req)
				.andExpect(status().isFound())
				.andExpect(header().string("Location", containsString("/payment-result.html")))
				.andExpect(header().string("Location", containsString("paymentCode=" + code)))
				.andExpect(header().string("Location", containsString("result=SUCCESS")));
	}

	@Test
	void purchase_withUnsupportedMethod_isRejected() throws Exception {
		purchaseRequest(MEMBER3, "MOMO")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PAYMENT_METHOD_NOT_SUPPORTED"));
	}

	@Test
	void expiredOnlinePayments_areCancelled_andLateIpnIgnored() throws Exception {
		JsonNode payment = purchaseVnpay(MEMBER3);

		int cancelled = paymentService.cancelExpiredOnline(LocalDateTime.now().plusMinutes(1));
		assertTrue(cancelled >= 1);
		getAs(MEMBER3, "/api/payments/" + payment.get("id").asLong())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		ipn(signedIpn(payment.get("code").asText(), GYM_1M_VNP_AMOUNT, "00"))
				.andExpect(jsonPath("$.RspCode").value("02"));
	}

	@Test
	void paymentHistory_isPaged() throws Exception {
		purchaseVnpay(MEMBER3);
		getAs(MEMBER3, "/api/payments/me?size=2")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	private JsonNode purchaseVnpay(String email) throws Exception {
		return readJson(purchase(email, "VNPAY")).get("payment");
	}

	private ResultActions purchase(String email, String method) throws Exception {
		return purchaseRequest(email, method).andExpect(status().isCreated());
	}

	private ResultActions purchaseRequest(String email, String method) throws Exception {
		return mvc.perform(auth(post("/api/subscriptions"), email)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"packageId\":%d,\"method\":\"%s\"}".formatted(GYM_1M, method)));
	}

	/** Tham số VNPay gửi về, đã ký bằng secret của test. */
	private Map<String, String> signedIpn(String txnRef, String amount, String responseCode) {
		Map<String, String> p = new HashMap<>();
		p.put("vnp_TmnCode", "TESTTMN1");
		p.put("vnp_TxnRef", txnRef);
		p.put("vnp_Amount", amount);
		p.put("vnp_ResponseCode", responseCode);
		p.put("vnp_TransactionStatus", responseCode);
		p.put("vnp_TransactionNo", "14000001");
		p.put("vnp_BankCode", "NCB");
		p.put("vnp_OrderInfo", "Thanh toan don " + txnRef);
		p.put("vnp_PayDate", LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
				.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
		p.put(VnpayService.PARAM_SECURE_HASH, vnpayService.sign(p));
		return p;
	}

	private ResultActions ipn(Map<String, String> params) throws Exception {
		MockHttpServletRequestBuilder req = get("/api/payments/vnpay/ipn");
		params.forEach(req::param);
		return mvc.perform(req);
	}
}
