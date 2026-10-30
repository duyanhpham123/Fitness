package com.duyanh.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.duyanh.dto.fitnessclass.ClassResponse;
import com.duyanh.dto.fitnessclass.ClassSessionResponse;
import com.duyanh.entity.ClassSession;
import com.duyanh.entity.FitnessClass;

@Mapper
public interface ClassMapper {

	ClassResponse toResponse(FitnessClass fitnessClass);

	@Mapping(target = "classId", source = "fitnessClass.id")
	@Mapping(target = "className", source = "fitnessClass.name")
	@Mapping(target = "trainerId", source = "trainer.id")
	@Mapping(target = "trainerName", source = "trainer.user.fullName")
	ClassSessionResponse toResponse(ClassSession session);
}
