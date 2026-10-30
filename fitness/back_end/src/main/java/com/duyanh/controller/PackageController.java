package com.duyanh.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.duyanh.dto.membership.PackageRequest;
import com.duyanh.dto.membership.PackageResponse;
import com.duyanh.exception.ApiException;
import com.duyanh.security.UserPrincipal;
import com.duyanh.service.PackageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Membership - Gói tập")
@RestController
@RequestMapping("/api/packages")
@RequiredArgsConstructor
public class PackageController {

	private final PackageService packageService;

	@Operation(summary = "Danh sách gói tập đang bán (không cần đăng nhập). ADMIN thêm includeInactive=true để xem cả gói ngừng bán")
	@GetMapping
	public List<PackageResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive,
			@AuthenticationPrincipal UserPrincipal principal) {
		if (includeInactive && (principal == null || !principal.getRoles().contains("ADMIN"))) {
			throw ApiException.forbidden("FORBIDDEN", "Chỉ ADMIN được xem gói đã ngừng bán");
		}
		return packageService.list(includeInactive);
	}

	@Operation(summary = "Tạo gói tập (ADMIN)")
	@PreAuthorize("hasRole('ADMIN')")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PackageResponse create(@Valid @RequestBody PackageRequest req) {
		return packageService.create(req);
	}

	@Operation(summary = "Sửa gói tập / ngừng bán bằng active=false (ADMIN)")
	@PreAuthorize("hasRole('ADMIN')")
	@PutMapping("/{id}")
	public PackageResponse update(@PathVariable Long id, @Valid @RequestBody PackageRequest req) {
		return packageService.update(id, req);
	}
}
