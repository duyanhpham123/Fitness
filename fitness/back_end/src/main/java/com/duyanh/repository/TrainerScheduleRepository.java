package com.duyanh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.duyanh.entity.TrainerSchedule;

public interface TrainerScheduleRepository extends JpaRepository<TrainerSchedule, Long> {

	List<TrainerSchedule> findByTrainerIdOrderByDayOfWeekAscStartTimeAsc(Long trainerId);

	List<TrainerSchedule> findByTrainerIdAndDayOfWeekOrderByStartTimeAsc(Long trainerId, Integer dayOfWeek);

	// Xóa bằng câu DELETE chạy ngay: deleteBy... sinh sẵn sẽ xóa sau khi insert lúc flush,
	// làm lịch mới đụng unique key (trainer_id, day_of_week, start_time) của lịch cũ
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from TrainerSchedule s where s.trainer.id = :trainerId")
	int deleteAllOfTrainer(@Param("trainerId") Long trainerId);
}
