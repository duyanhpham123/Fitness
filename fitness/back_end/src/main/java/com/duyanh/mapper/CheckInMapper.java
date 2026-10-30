package com.duyanh.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.duyanh.dto.checkin.CheckInResponse;
import com.duyanh.entity.CheckIn;

@Mapper
public interface CheckInMapper {

	@Mapping(target = "memberId", source = "user.id")
	@Mapping(target = "memberName", source = "user.fullName")
	@Mapping(target = "subscriptionId", source = "subscription.id")
	@Mapping(target = "packageName", source = "subscription.membershipPackage.name")
	@Mapping(target = "staffName", source = "staff.fullName")
	CheckInResponse toResponse(CheckIn checkIn);
}
