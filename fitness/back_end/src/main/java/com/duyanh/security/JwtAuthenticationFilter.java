package com.duyanh.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.duyanh.security.JwtService.ParsedToken;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Đọc header "Authorization: Bearer <token>" và gắn người dùng vào SecurityContext.
 * Token lỗi thì không chặn ở đây: ghi lý do vào request attribute, để
 * RestAuthenticationEntryPoint trả 401 với code TOKEN_EXPIRED / TOKEN_INVALID / TOKEN_REVOKED
 * (nếu endpoint yêu cầu đăng nhập).
 *
 * Không đánh dấu @Component để Spring Boot không tự đăng ký filter này lần thứ hai
 * ngoài Security filter chain.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	public static final String AUTH_ERROR_ATTRIBUTE = "jwt.error";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final TokenBlacklistService blacklistService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String token = extractBearerToken(request);
		if (token != null) {
			try {
				ParsedToken parsed = jwtService.parseAccessToken(token);
				if (blacklistService.isRevoked(parsed)) {
					request.setAttribute(AUTH_ERROR_ATTRIBUTE, "TOKEN_REVOKED");
				} else {
					UserPrincipal principal = parsed.principal();
					var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null,
							principal.getAuthorities());
					auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
					SecurityContextHolder.getContext().setAuthentication(auth);
				}
			} catch (ExpiredJwtException e) {
				request.setAttribute(AUTH_ERROR_ATTRIBUTE, "TOKEN_EXPIRED");
			} catch (JwtException | IllegalArgumentException e) {
				request.setAttribute(AUTH_ERROR_ATTRIBUTE, "TOKEN_INVALID");
			}
		}
		chain.doFilter(request, response);
	}

	public static String extractBearerToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		return header != null && header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()) : null;
	}
}
