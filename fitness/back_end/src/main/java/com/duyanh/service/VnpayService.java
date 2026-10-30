package com.duyanh.service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.duyanh.config.VnpayProperties;
import com.duyanh.entity.Payment;
import com.duyanh.security.HashUtils;

import lombok.RequiredArgsConstructor;

/**
 * Tạo URL thanh toán và kiểm tra chữ ký theo tài liệu VNPay 2.1.0.
 *
 * Chữ ký: sắp xếp tham số theo tên (a-z), nối "key=URLEncode(value)" bằng "&",
 * rồi HMAC-SHA512 với hashSecret.
 */
@Service
@RequiredArgsConstructor
public class VnpayService {

	public static final String PARAM_SECURE_HASH = "vnp_SecureHash";
	private static final String PARAM_SECURE_HASH_TYPE = "vnp_SecureHashType";
	private static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	// VNPay dùng giờ Việt Nam (GMT+7) cho mọi trường ngày giờ
	private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private final VnpayProperties props;

	public PaymentUrl buildPaymentUrl(Payment payment, String clientIp) {
		ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
		ZonedDateTime expire = now.plusMinutes(props.expireMinutes());

		Map<String, String> params = new TreeMap<>();
		params.put("vnp_Version", "2.1.0");
		params.put("vnp_Command", "pay");
		params.put("vnp_TmnCode", props.tmnCode());
		params.put("vnp_Amount", String.valueOf(toVnpAmount(payment.getAmount())));
		params.put("vnp_CurrCode", "VND");
		params.put("vnp_TxnRef", payment.getCode());
		// Không dấu, không ký tự đặc biệt theo khuyến nghị của VNPay
		params.put("vnp_OrderInfo", "Thanh toan don " + payment.getCode());
		params.put("vnp_OrderType", "other");
		params.put("vnp_Locale", "vn");
		params.put("vnp_ReturnUrl", props.returnUrl());
		params.put("vnp_IpAddr", clientIp);
		params.put("vnp_CreateDate", now.format(VNP_TIME));
		params.put("vnp_ExpireDate", expire.format(VNP_TIME));

		String query = toQuery(params);
		String url = props.payUrl() + "?" + query + "&" + PARAM_SECURE_HASH + "=" + hmacSha512(query);
		return new PaymentUrl(url, toServerTime(expire));
	}

	/**
	 * Kiểm tra chữ ký của tham số VNPay gửi về (IPN hoặc return URL).
	 */
	public boolean verifySignature(Map<String, String> params) {
		String received = params.get(PARAM_SECURE_HASH);
		if (received == null) {
			return false;
		}
		return HashUtils.constantTimeEquals(sign(params), received.toLowerCase());
	}

	/**
	 * Tính chữ ký cho bộ tham số (bỏ qua vnp_SecureHash / vnp_SecureHashType nếu có).
	 */
	public String sign(Map<String, String> params) {
		Map<String, String> sorted = new TreeMap<>(params);
		sorted.remove(PARAM_SECURE_HASH);
		sorted.remove(PARAM_SECURE_HASH_TYPE);
		return hmacSha512(toQuery(sorted));
	}

	// VNPay yêu cầu số tiền nhân 100 (bỏ phần thập phân)
	public static long toVnpAmount(BigDecimal amount) {
		return amount.movePointRight(2).longValueExact();
	}

	/**
	 * vnp_PayDate (giờ Việt Nam) -> LocalDateTime theo múi giờ của server, như mọi cột thời gian khác.
	 */
	public static LocalDateTime parsePayDate(String vnpPayDate) {
		return toServerTime(LocalDateTime.parse(vnpPayDate, VNP_TIME).atZone(VN_ZONE));
	}

	private static LocalDateTime toServerTime(ZonedDateTime time) {
		return time.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
	}

	private static String toQuery(Map<String, String> sortedParams) {
		return sortedParams.entrySet().stream()
				.filter(e -> e.getValue() != null && !e.getValue().isEmpty())
				.map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
				.collect(Collectors.joining("&"));
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.US_ASCII);
	}

	private String hmacSha512(String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA512");
			mac.init(new SecretKeySpec(props.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
			return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Không ký được dữ liệu VNPay", e);
		}
	}

	/**
	 * @param expiresAt thời điểm link hết hạn (giờ server)
	 */
	public record PaymentUrl(String url, LocalDateTime expiresAt) {
	}
}
