package com.duyanh.config;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.entity.User;
import com.duyanh.entity.enums.RoleCode;
import com.duyanh.entity.enums.UserStatus;
import com.duyanh.repository.RoleRepository;
import com.duyanh.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Tạo tài khoản ADMIN đầu tiên khi deploy (môi trường thật không có dữ liệu mẫu).
 *
 * Chỉ chạy khi đã đặt APP_BOOTSTRAP_ADMIN_EMAIL + APP_BOOTSTRAP_ADMIN_PASSWORD và hệ thống CHƯA có ADMIN nào,
 * nên khởi động lại nhiều lần cũng an toàn. Sau khi đăng nhập, nên đổi mật khẩu và xóa 2 biến này khỏi .env.
 */
@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

	private static final int MIN_PASSWORD_LENGTH = 12;

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final String email;
	private final String password;

	public AdminBootstrap(UserRepository userRepository, RoleRepository roleRepository,
			PasswordEncoder passwordEncoder,
			@Value("${app.bootstrap.admin-email:}") String email,
			@Value("${app.bootstrap.admin-password:}") String password) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.email = email;
		this.password = password;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (email.isBlank() || password.isBlank()) {
			return;
		}
		if (!userRepository.findActiveIds(RoleCode.ADMIN).isEmpty()) {
			log.info("Đã có tài khoản ADMIN, bỏ qua tạo admin đầu tiên (có thể xóa APP_BOOTSTRAP_ADMIN_* khỏi .env)");
			return;
		}
		if (password.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalStateException(
					"APP_BOOTSTRAP_ADMIN_PASSWORD phải dài ít nhất %d ký tự".formatted(MIN_PASSWORD_LENGTH));
		}
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		if (userRepository.existsByEmail(normalized)) {
			throw new IllegalStateException("Email " + normalized + " đã tồn tại nhưng không phải ADMIN");
		}

		User admin = new User();
		admin.setEmail(normalized);
		admin.setFullName("Quản trị viên");
		admin.setPasswordHash(passwordEncoder.encode(password));
		admin.setStatus(UserStatus.ACTIVE);
		admin.setEmailVerifiedAt(LocalDateTime.now());
		admin.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN).orElseThrow());
		userRepository.save(admin);
		log.warn("Đã tạo tài khoản ADMIN đầu tiên: {}. Hãy đăng nhập, đổi mật khẩu và xóa APP_BOOTSTRAP_ADMIN_* khỏi .env",
				normalized);
	}
}
