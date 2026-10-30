package com.duyanh.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.duyanh.entity.RefreshToken;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	// Khóa dòng khi refresh để 2 request dùng cùng 1 token không cùng rotate thành công
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :hash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String tokenHash);

	// Đăng xuất khỏi tất cả thiết bị, hoặc khi phát hiện token cũ bị dùng lại.
	// clearAutomatically: xóa cache của Hibernate để lần đọc sau thấy revokedAt mới
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
	int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

	@Modifying
	@Query("delete from RefreshToken t where t.expiresAt < :before")
	int deleteExpired(@Param("before") LocalDateTime before);
}
