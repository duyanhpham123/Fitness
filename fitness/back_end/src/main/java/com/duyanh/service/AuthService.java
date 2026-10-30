package com.duyanh.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.audit.AuditActions;
import com.duyanh.audit.Auditable;
import com.duyanh.config.OtpProperties;
import com.duyanh.dto.auth.AuthResponse;
import com.duyanh.dto.auth.LoginRequest;
import com.duyanh.dto.auth.RegisterRequest;
import com.duyanh.dto.auth.RegisterResponse;
import com.duyanh.dto.auth.ResetPasswordRequest;
import com.duyanh.dto.auth.VerifyEmailRequest;
import com.duyanh.dto.otp.OtpSentResponse;
import com.duyanh.entity.RefreshToken;
import com.duyanh.entity.Role;
import com.duyanh.entity.User;
import com.duyanh.entity.enums.OtpPurpose;
import com.duyanh.entity.enums.RoleCode;
import com.duyanh.entity.enums.UserStatus;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.UserMapper;
import com.duyanh.repository.RefreshTokenRepository;
import com.duyanh.repository.RoleRepository;
import com.duyanh.repository.UserRepository;
import com.duyanh.security.HashUtils;
import com.duyanh.security.JwtProperties;
import com.duyanh.security.JwtService;
import com.duyanh.security.LoginAttemptService;
import com.duyanh.security.TokenBlacklistService;
import com.duyanh.security.UserPrincipal;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Đăng ký (xác thực email bằng OTP), đăng nhập, refresh (có rotation), đăng xuất,
 * quên / đặt lại mật khẩu.
 *
 * Access token: JWT ngắn hạn (15 phút), không lưu DB; thu hồi sớm qua blacklist trên Redis.
 * Refresh token: chuỗi ngẫu nhiên, chỉ lưu SHA-256 hash. Mỗi lần refresh cấp token mới,
 * token cũ bị thu hồi. Nếu token đã bị thu hồi mà vẫn được dùng lại -> nghi bị đánh cắp
 * -> thu hồi toàn bộ phiên của user đó.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private static final String TOKEN_TYPE = "Bearer";
	private static final int REFRESH_TOKEN_BYTES = 32;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final AuthenticationManager authenticationManager;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final JwtProperties jwtProperties;
	private final TokenBlacklistService blacklistService;
	private final OtpService otpService;
	private final OtpProperties otpProperties;
	private final EmailService emailService;
	private final AuditService auditService;
	private final LoginAttemptService loginAttemptService;
	private final UserMapper userMapper;

	/**
	 * Tạo tài khoản PENDING và gửi mã OTP xác thực email.
	 * Chưa xác thực thì đăng nhập sẽ bị 403 ACCOUNT_NOT_VERIFIED.
	 */
	@Auditable(action = AuditActions.REGISTER, entityType = "USER", entityId = "#result.user().id()",
			actorId = "#result.user().id()")
	@Transactional
	public RegisterResponse register(RegisterRequest req) {
		String email = normalizeEmail(req.email());
		if (userRepository.existsByEmail(email)) {
			throw ApiException.conflict("EMAIL_EXISTS", "Email đã được sử dụng");
		}
		String phone = blankToNull(req.phone());
		if (phone != null && userRepository.existsByPhone(phone)) {
			throw ApiException.conflict("PHONE_EXISTS", "Số điện thoại đã được sử dụng");
		}
		Role memberRole = roleRepository.findByCode(RoleCode.MEMBER)
				.orElseThrow(() -> new IllegalStateException("Thiếu role MEMBER trong bảng roles"));

		User user = new User();
		user.setEmail(email);
		user.setPhone(phone);
		user.setFullName(req.fullName().trim());
		user.setPasswordHash(passwordEncoder.encode(req.password()));
		user.setStatus(UserStatus.PENDING);
		user.getRoles().add(memberRole);
		userRepository.save(user);
		log.info("Registered user id={} email={}", user.getId(), email);

		otpService.checkSendLimit(OtpPurpose.VERIFY_EMAIL, email);
		String code = sendOtp(OtpPurpose.VERIFY_EMAIL, email);
		return new RegisterResponse(userMapper.toResponse(user), otpService.sentResponse(code));
	}

	/**
	 * Gửi (lại) mã OTP. Luôn trả cùng một câu thông báo dù email có tồn tại hay không,
	 * để không ai dò được email nào đã đăng ký.
	 */
	@Transactional(readOnly = true)
	public OtpSentResponse requestOtp(String rawEmail, OtpPurpose purpose) {
		String email = normalizeEmail(rawEmail);
		otpService.checkSendLimit(purpose, email);

		boolean eligible = userRepository.findByEmail(email)
				.map(u -> switch (purpose) {
					case VERIFY_EMAIL -> u.getStatus() == UserStatus.PENDING;
					case RESET_PASSWORD -> u.getStatus() != UserStatus.LOCKED;
				})
				.orElse(false);
		String code = eligible ? sendOtp(purpose, email) : null;
		return otpService.sentResponse(code);
	}

	/**
	 * Xác thực email bằng OTP, kích hoạt tài khoản và đăng nhập luôn.
	 */
	@Auditable(action = AuditActions.EMAIL_VERIFY, entityType = "USER", entityId = "#result.user().id()",
			actorId = "#result.user().id()")
	@Transactional
	public AuthResponse verifyEmail(VerifyEmailRequest req, ClientInfo client) {
		String email = normalizeEmail(req.email());
		// Mã chỉ được tạo cho user PENDING, nên email không tồn tại sẽ báo OTP_EXPIRED như mã hết hạn
		otpService.verify(OtpPurpose.VERIFY_EMAIL, email, req.otp());

		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> ApiException.badRequest("OTP_EXPIRED", "Mã OTP không hợp lệ"));
		if (user.getStatus() != UserStatus.PENDING) {
			throw ApiException.conflict("EMAIL_ALREADY_VERIFIED", "Email đã được xác thực trước đó");
		}
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerifiedAt(LocalDateTime.now());
		user.setLastLoginAt(LocalDateTime.now());
		log.info("Email verified for user id={}", user.getId());

		return issueTokens(user, refreshExpiry(false), client).response();
	}

	/**
	 * Đặt lại mật khẩu bằng OTP. Sau khi đổi: thu hồi mọi refresh token và access token
	 * đang dùng của user, buộc đăng nhập lại trên mọi thiết bị.
	 */
	@Transactional
	public void resetPassword(ResetPasswordRequest req) {
		String email = normalizeEmail(req.email());
		otpService.verify(OtpPurpose.RESET_PASSWORD, email, req.otp());

		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> ApiException.badRequest("OTP_EXPIRED", "Mã OTP không hợp lệ"));
		if (user.getStatus() == UserStatus.LOCKED) {
			throw ApiException.forbidden("ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
		}
		user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
		// Nhận được OTP qua email nghĩa là đã chứng minh sở hữu email
		if (user.getStatus() == UserStatus.PENDING) {
			user.setStatus(UserStatus.ACTIVE);
			user.setEmailVerifiedAt(LocalDateTime.now());
		}

		refreshTokenRepository.revokeAllByUserId(user.getId(), LocalDateTime.now());
		auditService.record(user.getId(), AuditActions.PASSWORD_RESET, "USER", user.getId(), null);
		// Đặt lại mật khẩu thành công thì mở khóa đăng nhập (nếu đang bị khóa do nhập sai)
		loginAttemptService.reset(email);
		blacklistService.revokeAllForUser(user.getId());
		log.info("Password reset for user id={}", user.getId());
	}

	@Auditable(action = AuditActions.LOGIN, entityType = "USER", entityId = "#result.user().id()",
			actorId = "#result.user().id()", details = "{'rememberMe': #req.rememberMe()}")
	@Transactional
	public AuthResponse login(LoginRequest req, ClientInfo client) {
		String email = normalizeEmail(req.email());
		loginAttemptService.checkNotLocked(email);

		// Sai mật khẩu -> BadCredentialsException, bị khóa -> LockedException,
		// chưa xác thực email -> DisabledException (GlobalExceptionHandler xử lý)
		Authentication auth;
		try {
			auth = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(email, req.password()));
		} catch (BadCredentialsException e) {
			loginAttemptService.recordFailure(email);
			throw e;
		}
		loginAttemptService.reset(email);
		UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

		User user = userRepository.findById(principal.getId()).orElseThrow();
		user.setLastLoginAt(LocalDateTime.now());

		return issueTokens(user, refreshExpiry(req.rememberMe()), client).response();
	}

	// noRollbackFor: khi phát hiện dùng lại token, vẫn phải lưu việc thu hồi toàn bộ phiên rồi mới báo lỗi
	@Transactional(noRollbackFor = ApiException.class)
	public AuthResponse refresh(String rawRefreshToken, ClientInfo client) {
		RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(HashUtils.sha256Hex(rawRefreshToken))
				.orElseThrow(AuthService::invalidRefreshToken);
		LocalDateTime now = LocalDateTime.now();

		if (current.getRevokedAt() != null) {
			if (current.getReplacedBy() != null) {
				Long userId = current.getUser().getId();
				int revoked = refreshTokenRepository.revokeAllByUserId(userId, now);
				blacklistService.revokeAllForUser(userId);
				log.warn("Refresh token reuse detected for user id={}, revoked {} sessions", userId, revoked);
			}
			throw invalidRefreshToken();
		}
		if (!current.getExpiresAt().isAfter(now)) {
			throw ApiException.unauthorized("REFRESH_TOKEN_EXPIRED", "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
		}

		User user = current.getUser();
		if (user.getStatus() == UserStatus.LOCKED) {
			current.setRevokedAt(now);
			throw ApiException.forbidden("ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
		}

		// Token mới giữ nguyên hạn của token cũ: phiên đăng nhập không kéo dài vô hạn
		IssuedTokens issued = issueTokens(user, current.getExpiresAt(), client);
		current.setRevokedAt(now);
		current.setReplacedBy(issued.refreshToken());
		return issued.response();
	}

	/**
	 * Thu hồi refresh token, và đưa access token (nếu gửi kèm header Authorization) vào blacklist.
	 * Luôn thành công (kể cả token không tồn tại) để không lộ thông tin.
	 */
	@Transactional
	public void logout(String rawRefreshToken, String accessToken) {
		refreshTokenRepository.findByTokenHash(HashUtils.sha256Hex(rawRefreshToken))
				.filter(t -> t.getRevokedAt() == null)
				.ifPresent(t -> t.setRevokedAt(LocalDateTime.now()));

		if (accessToken != null) {
			try {
				blacklistService.blacklist(jwtService.parseAccessToken(accessToken));
			} catch (JwtException | IllegalArgumentException e) {
				// Token đã hết hạn hoặc không hợp lệ: không cần blacklist
			}
		}
	}

	private String sendOtp(OtpPurpose purpose, String email) {
		String code = otpService.generate(purpose, email);
		emailService.sendOtp(email, code, purpose, otpProperties.expirationSeconds());
		return code;
	}

	private LocalDateTime refreshExpiry(boolean rememberMe) {
		long ttlMs = rememberMe ? jwtProperties.refreshExpirationRememberMs() : jwtProperties.refreshExpirationMs();
		return LocalDateTime.now().plus(Duration.ofMillis(ttlMs));
	}

	private IssuedTokens issueTokens(User user, LocalDateTime refreshExpiresAt, ClientInfo client) {
		String rawRefresh = generateRefreshToken();

		RefreshToken entity = new RefreshToken();
		entity.setUser(user);
		entity.setTokenHash(HashUtils.sha256Hex(rawRefresh));
		entity.setExpiresAt(refreshExpiresAt);
		entity.setUserAgent(truncate(client.userAgent(), 255));
		entity.setIpAddress(truncate(client.ipAddress(), 45));
		refreshTokenRepository.save(entity);

		String accessToken = jwtService.generateAccessToken(UserPrincipal.from(user));
		AuthResponse response = new AuthResponse(accessToken, rawRefresh, TOKEN_TYPE,
				jwtService.getAccessExpirationSeconds(), refreshExpiresAt, userMapper.toResponse(user));
		return new IssuedTokens(response, entity);
	}

	private static String generateRefreshToken() {
		byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static ApiException invalidRefreshToken() {
		return ApiException.unauthorized("REFRESH_TOKEN_INVALID", "Refresh token không hợp lệ");
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static String truncate(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}

	/**
	 * Thông tin thiết bị lưu kèm refresh token (xem danh sách phiên đăng nhập).
	 */
	public record ClientInfo(String userAgent, String ipAddress) {
	}

	private record IssuedTokens(AuthResponse response, RefreshToken refreshToken) {
	}
}
