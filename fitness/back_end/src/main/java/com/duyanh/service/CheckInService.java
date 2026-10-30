package com.duyanh.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.audit.AuditActions;
import com.duyanh.audit.Auditable;
import com.duyanh.dto.checkin.CheckInResponse;
import com.duyanh.dto.checkin.QrCodeResponse;
import com.duyanh.dto.common.PageResponse;
import com.duyanh.entity.CheckIn;
import com.duyanh.entity.Subscription;
import com.duyanh.entity.User;
import com.duyanh.entity.enums.CheckInMethod;
import com.duyanh.entity.enums.RoleCode;
import com.duyanh.entity.enums.UserStatus;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.CheckInMapper;
import com.duyanh.repository.CheckInRepository;
import com.duyanh.repository.SubscriptionRepository;
import com.duyanh.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Check-in tại quầy.
 *
 * QR: app hội viên lấy mã ngắn hạn (lưu Redis "checkin:qr:{mã}" -> userId, TTL 60 giây),
 * lễ tân quét mã -> check-in. Mã bị xóa ngay khi quét nên chụp màn hình gửi người khác cũng
 * chỉ dùng được 1 lần và trong 60 giây.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInService {

	private static final Duration QR_TTL = Duration.ofSeconds(60);
	private static final String QR_KEY_PREFIX = "checkin:qr:";
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int MAX_PAGE_SIZE = 100;

	private final CheckInRepository checkInRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final UserRepository userRepository;
	private final StringRedisTemplate redis;
	private final CheckInMapper mapper;

	public QrCodeResponse createQr(Long memberId) {
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		redis.opsForValue().set(QR_KEY_PREFIX + token, memberId.toString(), QR_TTL);
		return new QrCodeResponse(token, QR_TTL.toSeconds());
	}

	@Auditable(action = AuditActions.CHECK_IN, entityType = "CHECK_IN", entityId = "#result.id()",
			details = "{'method': 'QR', 'memberId': #result.memberId()}")
	@Transactional
	public CheckInResponse checkInByQr(String qrToken, Long staffId) {
		// getAndDelete: 2 lần quét cùng lúc thì chỉ 1 lần lấy được userId
		String memberId = redis.opsForValue().getAndDelete(QR_KEY_PREFIX + qrToken);
		if (memberId == null) {
			throw ApiException.badRequest("QR_INVALID", "Mã QR không hợp lệ hoặc đã hết hạn, hãy làm mới mã");
		}
		return checkIn(Long.valueOf(memberId), staffId, CheckInMethod.QR);
	}

	@Auditable(action = AuditActions.CHECK_IN, entityType = "CHECK_IN", entityId = "#result.id()",
			details = "{'method': 'MANUAL', 'memberId': #memberId}")
	@Transactional
	public CheckInResponse checkInManual(Long memberId, Long staffId) {
		return checkIn(memberId, staffId, CheckInMethod.MANUAL);
	}

	@Auditable(action = AuditActions.CHECK_OUT, entityType = "CHECK_IN", entityId = "#checkInId")
	@Transactional
	public CheckInResponse checkOut(Long checkInId) {
		CheckIn checkIn = checkInRepository.findWithDetailsById(checkInId)
				.orElseThrow(() -> ApiException.notFound("CHECK_IN_NOT_FOUND", "Không tìm thấy lượt check-in"));
		if (checkIn.getCheckedOutAt() != null) {
			throw ApiException.conflict("ALREADY_CHECKED_OUT", "Lượt này đã check-out");
		}
		checkIn.setCheckedOutAt(LocalDateTime.now());
		return mapper.toResponse(checkIn);
	}

	@Transactional(readOnly = true)
	public PageResponse<CheckInResponse> listMine(Long memberId, int page, int size) {
		var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
		return PageResponse.of(checkInRepository.findByUserIdOrderByCheckedInAtDesc(memberId, pageable),
				mapper::toResponse);
	}

	private CheckInResponse checkIn(Long memberId, Long staffId, CheckInMethod method) {
		User member = userRepository.findById(memberId)
				.filter(u -> u.getRoles().stream().anyMatch(r -> r.getCode() == RoleCode.MEMBER))
				.orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "Không tìm thấy hội viên"));
		if (member.getStatus() != UserStatus.ACTIVE) {
			throw ApiException.forbidden("ACCOUNT_NOT_ACTIVE", "Tài khoản hội viên đang bị khóa hoặc chưa kích hoạt");
		}
		LocalDate today = LocalDate.now();
		Subscription sub = subscriptionRepository.findUsable(memberId, today)
				.orElseThrow(() -> ApiException.badRequest("NO_ACTIVE_SUBSCRIPTION",
						"Hội viên không có gói tập còn hiệu lực hôm nay"));
		// Lượt chưa check-out của ngày hôm trước coi như quên check-out, không chặn
		if (checkInRepository.findFirstByUserIdAndCheckedOutAtIsNullAndCheckedInAtGreaterThanEqualOrderByCheckedInAtDesc(
				memberId, today.atStartOfDay()).isPresent()) {
			throw ApiException.conflict("ALREADY_CHECKED_IN", "Hội viên đang ở phòng tập (chưa check-out)");
		}

		CheckIn checkIn = new CheckIn();
		checkIn.setUser(member);
		checkIn.setSubscription(sub);
		checkIn.setCheckedInAt(LocalDateTime.now());
		checkIn.setMethod(method);
		checkIn.setStaff(userRepository.getReferenceById(staffId));
		checkInRepository.save(checkIn);
		log.info("Member id={} checked in ({}) by staff id={}", memberId, method, staffId);
		return mapper.toResponse(checkInRepository.findWithDetailsById(checkIn.getId()).orElseThrow());
	}
}
