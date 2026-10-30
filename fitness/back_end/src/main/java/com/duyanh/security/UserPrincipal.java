package com.duyanh.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.duyanh.entity.User;
import com.duyanh.entity.enums.UserStatus;

import lombok.Getter;

/**
 * Người dùng đang đăng nhập. Lấy trong controller bằng @AuthenticationPrincipal UserPrincipal.
 * Authority có dạng ROLE_ADMIN, ROLE_MEMBER... để dùng được hasRole("ADMIN").
 */
@Getter
public class UserPrincipal implements UserDetails {

	private static final String ROLE_PREFIX = "ROLE_";

	private final Long id;
	private final String email;
	private final String password;
	private final UserStatus status;
	private final List<GrantedAuthority> authorities;

	private UserPrincipal(Long id, String email, String password, UserStatus status, List<String> roles) {
		this.id = id;
		this.email = email;
		this.password = password;
		this.status = status;
		this.authorities = roles.stream()
				.map(r -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + r))
				.toList();
	}

	// Dùng khi đăng nhập: load từ DB, có mật khẩu để so khớp BCrypt
	public static UserPrincipal from(User user) {
		List<String> roles = user.getRoles().stream().map(r -> r.getCode().name()).toList();
		return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getStatus(), roles);
	}

	// Dùng khi xác thực request: dựng lại từ claim trong access token, không truy vấn DB
	public static UserPrincipal fromToken(Long id, String email, List<String> roles) {
		return new UserPrincipal(id, email, null, UserStatus.ACTIVE, roles);
	}

	public List<String> getRoles() {
		return authorities.stream().map(a -> a.getAuthority().substring(ROLE_PREFIX.length())).toList();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonLocked() {
		return status != UserStatus.LOCKED;
	}

	// PENDING = chưa xác thực email
	@Override
	public boolean isEnabled() {
		return status != UserStatus.PENDING;
	}
}
