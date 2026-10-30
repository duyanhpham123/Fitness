package com.duyanh.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.duyanh.dto.trainer.ScheduleItem;
import com.duyanh.dto.trainer.TrainerResponse;
import com.duyanh.entity.TrainerProfile;
import com.duyanh.entity.TrainerSchedule;

@Mapper
public interface TrainerMapper {

	@Mapping(target = "fullName", source = "user.fullName")
	@Mapping(target = "avatarUrl", source = "user.avatarUrl")
	TrainerResponse toResponse(TrainerProfile trainer);

	ScheduleItem toItem(TrainerSchedule schedule);
}
