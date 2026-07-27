package io.openaev.notification.model;

import io.openaev.database.model.ResourceType;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class NotificationEvent {
  private ResourceType resourceType;
  private String resourceId;
  private NotificationEventType eventType;
  private Instant timestamp;
}
