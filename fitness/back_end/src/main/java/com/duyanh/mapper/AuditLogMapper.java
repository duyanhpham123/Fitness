package com.duyanh.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.duyanh.dto.audit.AuditLogResponse;
import com.duyanh.entity.AuditLog;

@Mapper
public interface AuditLogMapper {

	@Mapping(target = "actorId", source = "actor.id")
	@Mapping(target = "actorEmail", source = "actor.email")
	AuditLogResponse toResponse(AuditLog log);
}
