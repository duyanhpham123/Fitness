package com.duyanh.dto.admin;

import java.util.Set;

import com.duyanh.entity.enums.RoleCode;

import jakarta.validation.constraints.NotEmpty;

public record UpdateRolesRequest(

		@NotEmpty(message = "Tài khoản phải có ít nhất 1 vai trò")
		Set<RoleCode> roles) {
}
