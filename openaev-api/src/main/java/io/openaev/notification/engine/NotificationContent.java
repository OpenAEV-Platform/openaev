package io.openaev.notification.engine;

import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.ResourceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Content shape of a notification, mirroring OpenCTI's {@code notification_content}. */
public final class NotificationContent {

  private NotificationContent() {}

  /** A single matched event inside a notification. */
  public record Event(
      NotificationTriggerEventType operation,
      String message,
      ResourceType resourceType,
      String resourceId) {

    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("operation", operation != null ? operation.name() : null);
      map.put("message", message);
      map.put("resource_type", resourceType != null ? resourceType.name() : null);
      map.put("resource_id", resourceId);
      return map;
    }
  }

  /** A titled group of events (one per trigger for digests, a single group for live). */
  public record Group(String title, List<Event> events) {

    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("title", title);
      map.put("events", events.stream().map(Event::toMap).toList());
      return map;
    }
  }

  public static List<Map<String, Object>> toContentJson(List<Group> groups) {
    return groups.stream().map(Group::toMap).toList();
  }
}
