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

import com.duyanh.entity.Booking;
import com.duyanh.entity.enums.BookingStatus;

/**
 * "Đang hiệu lực" = status PENDING hoặc CONFIRMED (khớp với cột sinh tự động trong DB).
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

	@EntityGraph(attributePaths = { "member", "trainer", "trainer.user", "classSession", "classSession.fitnessClass" })
	Page<Booking> findByMemberIdOrderByStartAtDesc(Long memberId, Pageable pageable);

	@EntityGraph(attributePaths = { "member", "trainer", "trainer.user", "classSession", "classSession.fitnessClass" })
	Optional<Booking> findByIdAndMemberId(Long id, Long memberId);

	@EntityGraph(attributePaths = { "member", "trainer", "trainer.user" })
	List<Booking> findByTrainerIdAndStartAtBetweenOrderByStartAtAsc(Long trainerId, LocalDateTime from,
			LocalDateTime to);

	@EntityGraph(attributePaths = { "member", "trainer", "trainer.user", "classSession", "classSession.fitnessClass" })
	Optional<Booking> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

	// Lịch PT đang hiệu lực của HLV giao với khoảng [from, to) -> dùng tính slot trống
	@Query("""
			select b from Booking b
			where b.trainer.id = :trainerId and b.type = 'PT'
			  and b.status in ('PENDING', 'CONFIRMED')
			  and b.startAt < :to and b.endAt > :from
			""")
	List<Booking> findActivePtOfTrainer(@Param("trainerId") Long trainerId, @Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to);

	// Hội viên đã có lịch (PT hoặc lớp) trùng giờ chưa
	@Query("""
			select count(b) > 0 from Booking b
			where b.member.id = :memberId and b.status in ('PENDING', 'CONFIRMED')
			  and b.startAt < :to and b.endAt > :from
			""")
	boolean existsActiveOverlap(@Param("memberId") Long memberId, @Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to);

	@Query("""
			select count(b) > 0 from Booking b
			where b.member.id = :memberId and b.classSession.id = :sessionId
			  and b.status in ('PENDING', 'CONFIRMED')
			""")
	boolean existsActiveForSession(@Param("memberId") Long memberId, @Param("sessionId") Long sessionId);

	// Lịch hẹn hôm nay (dashboard)
	@EntityGraph(attributePaths = { "member", "trainer", "trainer.user", "classSession", "classSession.fitnessClass" })
	List<Booking> findByStartAtBetweenAndStatusInOrderByStartAtAsc(LocalDateTime from, LocalDateTime to,
			List<BookingStatus> statuses);

	// Lượt đặt lịch tạo trong [from, to), không tính lịch đã hủy
	@Query("""
			select count(b) from Booking b
			where b.createdAt >= :from and b.createdAt < :to and b.status <> 'CANCELLED'
			""")
	long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
