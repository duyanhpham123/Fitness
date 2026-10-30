package com.duyanh.controller;

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

import com.duyanh.dto.admin.CreateUserRequest;
import com.duyanh.dto.admin.MemberSummaryResponse;
import com.duyanh.dto.admin.UpdateRolesRequest;
import com.duyanh.dto.admin.UpdateStatusRequest;
import com.duyanh.dto.admin.UpdateUserRequest;
import com.duyanh.dto.common.PageResponse;
import com.duyanh.dto.user.UserResponse;
import com.duyanh.entity.enums.RoleCode;
import com.duyanh.entity.enums.UserStatus;
import com.duyanh.security.UserPrincipal;
import com.duyanh.service.AdminUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin - Tài khoản")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

	private final AdminUserService adminUserService;

	@Operation(summary = "Danh sách tài khoản, lọc theo role / trạng thái / từ khóa (tên, email, SĐT)")
	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping("/users")
	public PageResponse<UserResponse> search(
			@RequestParam(required = false) RoleCode role,
			@RequestParam(required = false) UserStatus status,
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return adminUserService.search(role, status, keyword, page, size);
	}

	@Operation(summary = "Tạo tài khoản nhân viên / HLV / hội viên tại quầy (kích hoạt ngay)")
	@PreAuthorize("hasRole('ADMIN')")
	@PostMapping("/users")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
		return adminUserService.create(req);
	}

	@Operation(summary = "Sửa thông tin cá nhân của tài khoản")
	@PreAuthorize("hasRole('ADMIN')")
	@PutMapping("/users/{id}")
	public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
		return adminUserService.update(id, req);
	}

	@Operation(summary = "Khóa (LOCKED) / mở khóa (ACTIVE) tài khoản; khóa sẽ đăng xuất khỏi mọi thiết bị")
	@PreAuthorize("hasRole('ADMIN')")
	@PutMapping("/users/{id}/status")
	public UserResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req,
			@AuthenticationPrincipal UserPrincipal admin) {
		return adminUserService.updateStatus(id, req.status(), admin.getId());
	}

	@Operation(summary = "Thay toàn bộ vai trò; user phải đăng nhập lại để nhận quyền mới")
	@PreAuthorize("hasRole('ADMIN')")
	@PutMapping("/users/{id}/roles")
	public UserResponse updateRoles(@PathVariable Long id, @Valid @RequestBody UpdateRolesRequest req,
			@AuthenticationPrincipal UserPrincipal admin) {
		return adminUserService.updateRoles(id, req.roles(), admin.getId());
	}

	@Operation(summary = "Danh sách hội viên kèm gói tập gần nhất (ADMIN, STAFF)")
	@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
	@GetMapping("/members")
	public PageResponse<MemberSummaryResponse> listMembers(
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return adminUserService.listMembers(keyword, page, size);
	}
}
