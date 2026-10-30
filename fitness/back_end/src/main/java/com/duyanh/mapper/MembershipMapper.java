package com.duyanh.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.duyanh.dto.membership.PackageRequest;
import com.duyanh.dto.membership.PackageResponse;
import com.duyanh.dto.membership.SubscriptionResponse;
import com.duyanh.dto.payment.PaymentResponse;
import com.duyanh.entity.MembershipPackage;
import com.duyanh.entity.Payment;
import com.duyanh.entity.Subscription;

@Mapper
public interface MembershipMapper {

	PackageResponse toResponse(MembershipPackage pkg);

	// active = null trong request thì giữ nguyên giá trị hiện tại
	@Mapping(target = "id", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	@Mapping(target = "active", source = "active", defaultExpression = "java(pkg.isActive())")
	void updateEntity(PackageRequest request, @MappingTarget MembershipPackage pkg);

	@Mapping(target = "packageId", source = "membershipPackage.id")
	@Mapping(target = "packageCode", source = "membershipPackage.code")
	@Mapping(target = "packageName", source = "membershipPackage.name")
	@Mapping(target = "remainingFreezeDays",
			expression = "java(sub.getMembershipPackage().getMaxFreezeDays() - sub.getFrozenDaysUsed())")
	SubscriptionResponse toResponse(Subscription sub);

	@Mapping(target = "subscriptionId", source = "subscription.id")
	PaymentResponse toResponse(Payment payment);
}
