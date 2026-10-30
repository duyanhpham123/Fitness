package com.duyanh.mapper;

import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;

import com.duyanh.dto.user.UserResponse;
import com.duyanh.entity.Role;
import com.duyanh.entity.User;

/**
 * MapStruct sinh class UserMapperImpl lúc compile (target/generated-sources).
 * Là Spring bean nhờ -Amapstruct.defaultComponentModel=spring trong pom.xml.
 */
@Mapper
public interface UserMapper {

	UserResponse toResponse(User user);

	default Set<String> mapRoles(Set<Role> roles) {
		return roles.stream().map(r -> r.getCode().name()).collect(Collectors.toSet());
	}
}
