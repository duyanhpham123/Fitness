package com.duyanh.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.duyanh.dto.booking.BookingResponse;
import com.duyanh.entity.Booking;

@Mapper
public interface BookingMapper {

	@Mapping(target = "memberId", source = "member.id")
	@Mapping(target = "memberName", source = "member.fullName")
	@Mapping(target = "trainerId", source = "trainer.id")
	@Mapping(target = "trainerName", source = "trainer.user.fullName")
	@Mapping(target = "classSessionId", source = "classSession.id")
	@Mapping(target = "className", source = "classSession.fitnessClass.name")
	@Mapping(target = "room", source = "classSession.room")
	BookingResponse toResponse(Booking booking);
}
