package com.duyanh.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.duyanh.entity.Payment;
import com.duyanh.entity.enums.PaymentMethod;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByCode(String code);

	// IPN callback: khóa dòng payment để 2 callback đến cùng lúc không cùng kích hoạt gói
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.code = :code")
	Optional<Payment> findByCodeForUpdate(@Param("code") String code);

	// Nhân viên xác nhận tiền mặt: khóa dòng để 2 lần bấm xác nhận không cùng kích hoạt gói
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p left join fetch p.subscription where p.id = :id")
	Optional<Payment> findByIdForUpdate(@Param("id") Long id);

	// Client gửi lại request với cùng Idempotency-Key -> trả lại payment cũ
	Optional<Payment> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

	Page<Payment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Payment p set p.status = 'CANCELLED'
			where p.status = 'PENDING' and p.method in :methods and p.createdAt < :cutoff
			""")
	int cancelExpired(@Param("methods") Collection<PaymentMethod> methods, @Param("cutoff") LocalDateTime cutoff);

	@Query("""
			select coalesce(sum(p.amount), 0) from Payment p
			where p.status = 'SUCCESS' and p.paidAt >= :from and p.paidAt < :to
			""")
	BigDecimal sumRevenue(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	// Từng giao dịch thành công [paidAt, amount]. Không gom theo ngày trong SQL vì DB lưu UTC,
	// "ngày" trong SQL sẽ lệch với ngày giờ Việt Nam; gom trong Java sau khi Hibernate đổi về giờ server.
	@Query("""
			select p.paidAt, p.amount from Payment p
			where p.status = 'SUCCESS' and p.paidAt >= :from and p.paidAt < :to
			""")
	List<Object[]> findPaidAmounts(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	// Gói bán chạy: [packageId, tên gói, số lượt bán, doanh thu]
	@Query("""
			select s.membershipPackage.id, s.membershipPackage.name, count(p), sum(p.amount)
			from Payment p join p.subscription s
			where p.status = 'SUCCESS' and p.paidAt >= :from and p.paidAt < :to
			group by s.membershipPackage.id, s.membershipPackage.name
			order by count(p) desc, sum(p.amount) desc
			""")
	List<Object[]> topPackages(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
