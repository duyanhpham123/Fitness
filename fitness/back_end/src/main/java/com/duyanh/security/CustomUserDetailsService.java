package com.duyanh.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.duyanh.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Spring Security gọi khi đăng nhập (AuthenticationManager.authenticate).
 * Spring Boot tự ghép bean này với PasswordEncoder thành DaoAuthenticationProvider.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.map(UserPrincipal::from)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}
}
