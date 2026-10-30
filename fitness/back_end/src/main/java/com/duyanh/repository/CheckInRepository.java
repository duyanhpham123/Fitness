package com.duyanh.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.duyanh.entity.CheckIn;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

	@EntityGraph(attributePaths = { "user", "subscription", "subscription.membershipPackage", "staff" })
	Page<CheckIn> findByUserIdOrderByCheckedInAtDesc(Long userId, Pageable pageable);

	@EntityGraph(attributePaths = { "user", "subscription", "subscription.membershipPackage", "staff" })
	Optional<CheckIn> findWithDetailsById(Long id);

	// Lượt check-in từ thời điểm cho trước (hôm nay), mới nhất trước: màn lễ tân
	@EntityGraph(attributePaths = { "user", "subscription", "subscription.membershipPackage", "staff" })
	List<CheckIn> findByCheckedInAtGreaterThanEqualOrderByCheckedInAtDesc(LocalDateTime since);

	// Lượt check-in chưa check-out của hội viên kể từ thời điểm cho trước (đầu ngày hôm nay)
	Optional<CheckIn> findFirstByUserIdAndCheckedOutAtIsNullAndCheckedInAtGreaterThanEqualOrderByCheckedInAtDesc(
			Long userId, LocalDateTime since);

	@Query("select c.checkedInAt from CheckIn c where c.checkedInAt >= :from and c.checkedInAt < :to")
	List<LocalDateTime> findCheckInTimes(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	// Số hội viên khác nhau đã đến tập trong khoảng thời gian (tính tỷ lệ đi tập)
	@Query("select count(distinct c.user.id) from CheckIn c where c.checkedInAt >= :from and c.checkedInAt < :to")
	long countDistinctMembers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
