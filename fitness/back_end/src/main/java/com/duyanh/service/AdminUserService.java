package com.duyanh.service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.audit.AuditActions;
import com.duyanh.audit.Auditable;
import com.duyanh.dto.admin.CreateUserRequest;
import com.duyanh.dto.admin.MemberSummaryResponse;
import com.duyanh.dto.admin.UpdateUserRequest;
import com.duyanh.dto.common.PageResponse;
import com.duyanh.dto.user.UserResponse;
import com.duyanh.entity.Role;
import com.duyanh.entity.Subscription;
import com.duyanh.entity.TrainerProfile;
import com.duyanh.entity.User;
import com.duyanh.entity.enums.RoleCode;
import com.duyanh.entity.enums.UserStatus;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.UserMapper;
import com.duyanh.repository.RefreshTokenRepository;
import com.duyanh.repository.RoleRepository;
import com.duyanh.repository.SubscriptionRepository;
import com.duyanh.repository.TrainerProfileRepository;
import com.duyanh.repository.UserRepository;
import com.duyanh.security.TokenBlacklistService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ADMIN quản lý tài khoản. Khóa tài khoản hoặc đổi vai trò sẽ thu hồi mọi phiên đăng nhập
 * của user đó (role nằm trong JWT nên phải đăng nhập lại để nhận quyền mới).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

	private static final int MAX_PAGE_SIZE = 100;
	// Admin chỉ được chuyển qua lại giữa 2 trạng thái này; PENDING là trạng thái chờ xác thực email
	private static final Set<UserStatus> SETTABLE_STATUSES = EnumSet.of(UserStatus.ACTIVE, UserStatus.LOCKED);

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final TrainerProfileRepository trainerRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final TokenBlacklistService blacklistService;
	private final PasswordEncoder passwordEncoder;
	private final UserMapper userMapper;

	@Transactional(readOnly = true)
	public PageResponse<UserResponse> search(RoleCode role, UserStatus status, String keyword, int page, int size) {
		return PageResponse.of(userRepository.search(role, status, blankToNull(keyword), pageable(page, size)),
				userMapper::toResponse);
	}

	@Auditable(action = AuditActions.USER_CREATE, entityType = "USER", entityId = "#result.id()",
			details = "{'email': #req.email(), 'role': #req.role()}")
	@Transactional
	public UserResponse create(CreateUserRequest req) {
		String email = req.email().trim().toLowerCase(Locale.ROOT);
		if (userRepository.existsByEmail(email)) {
			throw ApiException.conflict("EMAIL_EXISTS", "Email đã được sử dụng");
		}
		String phone = blankToNull(req.phone());
		if (phone != null && userRepository.existsByPhone(phone)) {
			throw ApiException.conflict("PHONE_EXISTS", "Số điện thoại đã được sử dụng");
		}

		User user = new User();
		user.setEmail(email);
		user.setPhone(phone);
		user.setFullName(req.fullName().trim());
		user.setPasswordHash(passwordEncoder.encode(req.password()));
		// Admin tạo tại quầy nên không cần xác thực email qua OTP
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerifiedAt(LocalDateTime.now());
		user.getRoles().add(role(req.role()));
		userRepository.save(user);

		if (req.role() == RoleCode.TRAINER) {
			ensureTrainerProfile(user, blankToNull(req.specialization()));
		}
		log.info("Admin created user id={} role={}", user.getId(), req.role());
		return userMapper.toResponse(user);
	}

	@Auditable(action = AuditActions.USER_UPDATE, entityType = "USER", entityId = "#userId", details = "#req")
	@Transactional
	public UserResponse update(Long userId, UpdateUserRequest req) {
		User user = findUser(userId);
		String phone = blankToNull(req.phone());
		if (phone != null && userRepository.existsByPhoneAndIdNot(phone, userId)) {
			throw ApiException.conflict("PHONE_EXISTS", "Số điện thoại đã được sử dụng");
		}
		user.setFullName(req.fullName().trim());
		user.setPhone(phone);
		user.setGender(req.gender());
		user.setDateOfBirth(req.dateOfBirth());
		user.setAddress(blankToNull(req.address()));
		return userMapper.toResponse(user);
	}

	/**
	 * Khóa: đăng xuất khỏi mọi thiết bị ngay lập tức. Mở khóa: đăng nhập lại bình thường.
	 */
	@Auditable(action = AuditActions.USER_STATUS_CHANGE, entityType = "USER", entityId = "#userId",
			details = "{'status': #status}")
	@Transactional
	public UserResponse updateStatus(Long userId, UserStatus status, Long adminId) {
		if (!SETTABLE_STATUSES.contains(status)) {
			throw ApiException.badRequest("INVALID_STATUS", "Chỉ được chuyển sang ACTIVE hoặc LOCKED");
		}
		if (userId.equals(adminId)) {
			throw ApiException.badRequest("CANNOT_MODIFY_SELF", "Không thể tự khóa tài khoản của mình");
		}
		User user = findUser(userId);
		user.setStatus(status);
		if (status == UserStatus.LOCKED) {
			revokeSessions(userId);
		}
		log.info("Admin id={} set user id={} status={}", adminId, userId, status);
		return userMapper.toResponse(user);
	}

	/**
	 * Thay toàn bộ vai trò. Thêm TRAINER thì tạo / mở lại hồ sơ HLV; bỏ TRAINER thì ẩn hồ sơ
	 * (giữ dữ liệu cũ vì còn lịch PT, buổi lớp tham chiếu tới).
	 */
	@Auditable(action = AuditActions.USER_ROLES_CHANGE, entityType = "USER", entityId = "#userId",
			details = "{'roles': #roles}")
	@Transactional
	public UserResponse updateRoles(Long userId, Set<RoleCode> roles, Long adminId) {
		if (userId.equals(adminId) && !roles.contains(RoleCode.ADMIN)) {
			throw ApiException.badRequest("CANNOT_MODIFY_SELF", "Không thể tự bỏ quyền ADMIN của mình");
		}
		User user = findUser(userId);
		Set<Role> newRoles = new HashSet<>();
		roles.forEach(code -> newRoles.add(role(code)));
		user.getRoles().clear();
		user.getRoles().addAll(newRoles);

		if (roles.contains(RoleCode.TRAINER)) {
			ensureTrainerProfile(user, null);
		} else {
			trainerRepository.findById(userId).ifPresent(t -> t.setActive(false));
		}
		revokeSessions(userId);
		return userMapper.toResponse(user);
	}

	/**
	 * Danh sách hội viên kèm gói gần nhất (màn "Danh sách hội viên").
	 */
	@Transactional(readOnly = true)
	public PageResponse<MemberSummaryResponse> listMembers(String keyword, int page, int size) {
		Page<User> members = userRepository.search(RoleCode.MEMBER, null, blankToNull(keyword), pageable(page, size));
		List<Long> ids = members.getContent().stream().map(User::getId).toList();

		// Lấy gói của cả trang trong 1 truy vấn; danh sách đã sắp xếp end_date giảm dần nên gói đầu tiên là mới nhất
		Map<Long, Subscription> latest = new HashMap<>();
		if (!ids.isEmpty()) {
			subscriptionRepository.findEffectiveOfUsers(ids).forEach(s -> latest.putIfAbsent(s.getUser().getId(), s));
		}
		return PageResponse.of(members, u -> {
			Subscription s = latest.get(u.getId());
			return new MemberSummaryResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getAvatarUrl(),
					u.getStatus(),
					s == null ? null : s.getMembershipPackage().getName(),
					s == null ? null : s.getStatus(),
					s == null ? null : s.getEndDate(),
					u.getCreatedAt());
		});
	}

	private void ensureTrainerProfile(User user, String specialization) {
		TrainerProfile profile = trainerRepository.findById(user.getId()).orElseGet(() -> {
			TrainerProfile p = new TrainerProfile();
			p.setUser(user);
			return p;
		});
		if (specialization != null) {
			profile.setSpecialization(specialization);
		}
		profile.setActive(true);
		trainerRepository.save(profile);
	}

	private void revokeSessions(Long userId) {
		refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
		blacklistService.revokeAllForUser(userId);
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Không tìm thấy tài khoản"));
	}

	private Role role(RoleCode code) {
		return roleRepository.findByCode(code)
				.orElseThrow(() -> new IllegalStateException("Thiếu role " + code + " trong bảng roles"));
	}

	private static PageRequest pageable(int page, int size) {
		return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
