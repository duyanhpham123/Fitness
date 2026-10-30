package com.duyanh.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.duyanh.entity.ClassSession;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

	@EntityGraph(attributePaths = { "fitnessClass", "trainer", "trainer.user" })
	List<ClassSession> findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(LocalDateTime from,
			LocalDateTime to);

	@EntityGraph(attributePaths = { "fitnessClass", "trainer", "trainer.user" })
	Optional<ClassSession> findWithDetailsById(Long id);

	// Buổi học HLV đang dạy giao với khoảng [from, to)
	@Query("""
			select s from ClassSession s
			where s.trainer.id = :trainerId and s.status = 'SCHEDULED'
			  and s.startAt < :to and s.endAt > :from
			""")
	List<ClassSession> findScheduledOfTrainer(@Param("trainerId") Long trainerId, @Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to);

	/**
	 * Giữ 1 chỗ bằng câu update nguyên tử. Trả về 0 = hết chỗ hoặc buổi học không còn mở.
	 * Không cần lock thủ công: MySQL tự khóa dòng trong lúc update.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update ClassSession s set s.bookedCount = s.bookedCount + 1
			where s.id = :id and s.status = 'SCHEDULED' and s.bookedCount < s.capacity
			""")
	int tryReserveSeat(@Param("id") Long id);

	// Trả lại chỗ khi hủy lịch
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update ClassSession s set s.bookedCount = s.bookedCount - 1 where s.id = :id and s.bookedCount > 0")
	int releaseSeat(@Param("id") Long id);
}
