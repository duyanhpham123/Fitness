package com.duyanh.controller;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.audit.AuditLogResponse;
import com.duyanh.dto.common.PageResponse;
import com.duyanh.service.AuditService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Audit Log")
@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditLogController {

	private final AuditService auditService;

	@Operation(summary = "Tra cứu log thao tác (ADMIN), lọc theo người thực hiện / hành động / đối tượng / thời gian")
	@GetMapping
	public PageResponse<AuditLogResponse> search(
			@RequestParam(required = false) Long actorId,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String entityType,
			@RequestParam(required = false) Long entityId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return auditService.search(actorId, action, entityType, entityId, from, to, page, size);
	}

	@Operation(summary = "Chi tiết 1 dòng log (ADMIN)")
	@GetMapping("/{id}")
	public AuditLogResponse get(@PathVariable Long id) {
		return auditService.get(id);
	}
}
