package io.openaev.notification.model;

import io.openaev.database.model.NotificationRuleResourceType;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class NotificationEvent {
  private NotificationRuleResourceType resourceType;
  private String resourceId;
  private NotificationEventType eventType;
  private Instant timestamp;
}
