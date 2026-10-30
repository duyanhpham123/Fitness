package com.duyanh.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.duyanh.dto.audit.AuditLogResponse;
import com.duyanh.dto.common.PageResponse;
import com.duyanh.entity.AuditLog;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.AuditLogMapper;
import com.duyanh.repository.AuditLogRepository;
import com.duyanh.repository.UserRepository;
import com.duyanh.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final AuditLogRepository auditLogRepository;
	private final UserRepository userRepository;
	private final AuditLogMapper mapper;
	private final ObjectMapper objectMapper;

	/**
	 * Ghi 1 dòng log, chạy chung transaction với thao tác nghiệp vụ.
	 * IP và User-Agent lấy từ request hiện tại (null nếu chạy từ job).
	 *
	 * @param actorId null = hệ thống (job, IPN của cổng thanh toán)
	 * @param details object bất kỳ (DTO, Map...), lưu dạng JSON vào new_value
	 */
	@Transactional
	public void record(Long actorId, String action, String entityType, Long entityId, Object details) {
		AuditLog log = new AuditLog();
		log.setActor(actorId == null ? null : userRepository.getReferenceById(actorId));
		log.setAction(action);
		log.setEntityType(entityType);
		log.setEntityId(entityId);
		log.setNewValue(details == null ? null : objectMapper.convertValue(details, MAP_TYPE));
		HttpServletRequest request = currentRequest();
		if (request != null) {
			log.setIpAddress(request.getRemoteAddr());
			String ua = request.getHeader(HttpHeaders.USER_AGENT);
			log.setUserAgent(ua == null || ua.length() <= 255 ? ua : ua.substring(0, 255));
		}
		auditLogRepository.save(log);
	}

	public Long currentUserId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.getPrincipal() instanceof UserPrincipal p ? p.getId() : null;
	}

	/**
	 * Tra cứu log (ADMIN). Mọi điều kiện lọc đều không bắt buộc.
	 */
	@Transactional(readOnly = true)
	public PageResponse<AuditLogResponse> search(Long actorId, String action, String entityType, Long entityId,
			LocalDateTime from, LocalDateTime to, int page, int size) {
		Specification<AuditLog> spec = (root, query, cb) -> {
			List<Predicate> where = new ArrayList<>();
			if (actorId != null) {
				where.add(cb.equal(root.get("actor").get("id"), actorId));
			}
			if (action != null && !action.isBlank()) {
				where.add(cb.equal(root.get("action"), action.trim().toUpperCase()));
			}
			if (entityType != null && !entityType.isBlank()) {
				where.add(cb.equal(root.get("entityType"), entityType.trim().toUpperCase()));
			}
			if (entityId != null) {
				where.add(cb.equal(root.get("entityId"), entityId));
			}
			if (from != null) {
				where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
			}
			if (to != null) {
				where.add(cb.lessThan(root.get("createdAt"), to));
			}
			return cb.and(where.toArray(Predicate[]::new));
		};
		var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "createdAt", "id"));
		return PageResponse.of(auditLogRepository.findAll(spec, pageable), mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public AuditLogResponse get(Long id) {
		return auditLogRepository.findById(id)
				.map(mapper::toResponse)
				.orElseThrow(() -> ApiException.notFound("AUDIT_LOG_NOT_FOUND", "Không tìm thấy log"));
	}

	private static HttpServletRequest currentRequest() {
		return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
				? attrs.getRequest()
				: null;
	}
}
