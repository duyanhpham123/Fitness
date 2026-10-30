package com.duyanh.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.duyanh.entity.Subscription;
import com.duyanh.entity.enums.PaymentMethod;
import com.duyanh.entity.enums.SubscriptionStatus;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

	@EntityGraph(attributePaths = "membershipPackage")
	List<Subscription> findByUserIdOrderByCreatedAtDesc(Long userId);

	@EntityGraph(attributePaths = "membershipPackage")
	Optional<Subscription> findByIdAndUserId(Long id, Long userId);

	// Gói còn hiệu lực tại ngày cho trước (dùng khi đặt lịch / check-in)
	@Query("""
			select s from Subscription s
			where s.user.id = :userId and s.status = 'ACTIVE'
			  and s.startDate <= :date and s.endDate >= :date
			order by s.endDate desc
			limit 1
			""")
	Optional<Subscription> findUsable(@Param("userId") Long userId, @Param("date") LocalDate date);

	// Ngày kết thúc xa nhất trong các gói còn hiệu lực của user, để gói mới nối tiếp phía sau
	@Query("""
			select max(s.endDate) from Subscription s
			where s.user.id = :userId and s.status in ('ACTIVE', 'FROZEN') and s.id <> :excludeId
			""")
	Optional<LocalDate> findLatestEndDate(@Param("userId") Long userId, @Param("excludeId") Long excludeId);

	// Gói đã mua nối tiếp, chưa tới ngày bắt đầu
	@Query("""
			select s from Subscription s
			where s.user.id = :userId and s.status = 'ACTIVE' and s.startDate > :date
			order by s.startDate
			""")
	List<Subscription> findQueued(@Param("userId") Long userId, @Param("date") LocalDate date);

	// Job hằng ngày: chuyển gói quá hạn sang EXPIRED
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Subscription s set s.status = 'EXPIRED' where s.status = 'ACTIVE' and s.endDate < :today")
	int expireBefore(@Param("today") LocalDate today);

	// Job hằng ngày: hết thời gian bảo lưu thì kích hoạt lại
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Subscription s set s.status = 'ACTIVE' where s.status = 'FROZEN' and s.frozenUntil < :today")
	int unfreezeDue(@Param("today") LocalDate today);

	// Hủy gói PENDING của các đơn thanh toán online bị bỏ dở (chạy trước khi hủy payment)
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Subscription s set s.status = 'CANCELLED'
			where s.status = 'PENDING' and s.id in (
			  select p.subscription.id from Payment p
			  where p.status = 'PENDING' and p.method in :methods and p.createdAt < :cutoff)
			""")
	int cancelPendingOfExpiredPayments(@Param("methods") Collection<PaymentMethod> methods,
			@Param("cutoff") LocalDateTime cutoff);

	// Gói đang dùng hết hạn đúng ngày cho trước (nhắc gia hạn)
	@Query("""
			select s from Subscription s join fetch s.user join fetch s.membershipPackage
			where s.status = 'ACTIVE' and s.endDate = :endDate
			""")
	List<Subscription> findActiveEndingOn(@Param("endDate") LocalDate endDate);

	// Gói gần nhất (đã từng có hiệu lực) của nhiều user, mới nhất trước: dùng cho danh sách hội viên
	@Query("""
			select s from Subscription s join fetch s.membershipPackage
			where s.user.id in :userIds and s.status in ('ACTIVE', 'FROZEN', 'EXPIRED')
			order by s.endDate desc
			""")
	List<Subscription> findEffectiveOfUsers(@Param("userIds") Collection<Long> userIds);

	// Số gói đang trong thời hạn tại 1 ngày (ACTIVE/FROZEN hôm nay, hoặc đã EXPIRED nếu xét quá khứ)
	@Query("""
			select count(s) from Subscription s
			where s.status in ('ACTIVE', 'FROZEN', 'EXPIRED') and s.startDate <= :date and s.endDate >= :date
			""")
	long countCovering(@Param("date") LocalDate date);

	// Số hội viên có gói giao với khoảng [from, to]
	@Query("""
			select count(distinct s.user.id) from Subscription s
			where s.status in ('ACTIVE', 'FROZEN', 'EXPIRED') and s.startDate <= :to and s.endDate >= :from
			""")
	long countMembersCovering(@Param("from") LocalDate from, @Param("to") LocalDate to);

	long countByStatus(SubscriptionStatus status);
}
