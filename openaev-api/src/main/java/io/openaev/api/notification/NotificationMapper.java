package io.openaev.api.notification;

import io.openaev.database.model.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

  public NotificationOutput toNotificationOutput(final Notification notification) {
    return NotificationOutput.builder()
        .id(notification.getId())
        .name(notification.getName())
        .type(notification.getType())
        .content(notification.getContent())
        .read(notification.isRead())
        .createdAt(notification.getCreatedAt())
        .build();
  }
}
