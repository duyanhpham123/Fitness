package com.duyanh.mapper;

import org.mapstruct.Mapper;

import com.duyanh.dto.notification.NotificationResponse;
import com.duyanh.entity.Notification;

@Mapper
public interface NotificationMapper {

	NotificationResponse toResponse(Notification notification);
}
