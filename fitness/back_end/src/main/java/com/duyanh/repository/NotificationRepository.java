package com.duyanh.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.duyanh.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

	long countByUserIdAndReadFalse(Long userId);

	boolean existsByIdAndUserId(Long id, Long userId);

	boolean existsByUserIdAndReferenceTypeAndReferenceId(Long userId, String referenceType, Long referenceId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Notification n set n.read = true, n.readAt = :now
			where n.id = :id and n.user.id = :userId and n.read = false
			""")
	int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Notification n set n.read = true, n.readAt = :now where n.user.id = :userId and n.read = false")
	int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
