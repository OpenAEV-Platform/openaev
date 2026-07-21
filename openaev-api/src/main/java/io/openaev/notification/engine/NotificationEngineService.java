package io.openaev.notification.engine;

import io.openaev.context.TenantContext;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.NotificationEventRecordRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Live stage of the notifications engine: for a single entity event, evaluates every live trigger
 * watching that resource type, records an outbox {@link NotificationEventRecord} per (trigger,
 * user) match (the digest source) and dispatches the trigger's notifiers immediately.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationEngineService {

  private final NotificationTriggerCacheService triggerCacheService;
  private final NotificationMatchingService matchingService;
  private final NotificationDispatchService dispatchService;
  private final NotificationEventRecordRepository notificationEventRecordRepository;

  /**
   * Processes one entity lifecycle event.
   *
   * @param entry catalog entry of the entity's resource type
   * @param entityId id of the affected entity
   * @param entityTenantId tenant of the affected entity (null when not tenant-scoped)
   * @param eventType lifecycle operation
   * @param label human-readable entity label used in notification messages
   */
  public void handleEvent(
      NotificationResourceCatalog entry,
      String entityId,
      String entityTenantId,
      NotificationTriggerEventType eventType,
      String label) {
    handleEventWithMessage(
        entry, entityId, entityTenantId, eventType, buildMessage(entry, eventType, label));
  }

  /**
   * Processes one event with a pre-built notification message. Used for semantic events (e.g.
   * scenario score degradation) whose message carries more context than a lifecycle operation.
   */
  public void handleEventWithMessage(
      NotificationResourceCatalog entry,
      String entityId,
      String entityTenantId,
      NotificationTriggerEventType eventType,
      String message) {
    List<ResolvedNotificationTrigger> triggers =
        triggerCacheService.getLiveTriggers(entry.getResourceType());
    if (triggers.isEmpty()) {
      return;
    }
    for (ResolvedNotificationTrigger trigger : triggers) {
      try {
        if (!trigger.eventTypes().contains(eventType)) {
          continue;
        }
        // Tenant isolation: a trigger only sees events of its own tenant
        if (entityTenantId != null && !entityTenantId.equals(trigger.tenantId())) {
          continue;
        }
        // The filter re-check must run with the trigger's tenant so the Hibernate tenant
        // filter (enabled by HibernateFilterTransactionAspect) scopes the query correctly.
        boolean matches;
        TenantContext.setCurrentTenant(trigger.tenantId());
        try {
          matches = matchingService.matches(trigger, entry, entityId);
        } finally {
          TenantContext.clearCurrentTenant();
        }
        if (!matches) {
          continue;
        }
        recordEvents(trigger, entry, entityId, eventType, message);
        NotificationContent.Group group =
            new NotificationContent.Group(
                trigger.name(),
                List.of(
                    new NotificationContent.Event(
                        eventType, message, entry.getResourceType(), entityId)));
        dispatchService.dispatch(
            trigger, NotificationTriggerType.LIVE, trigger.recipientUserIds(), List.of(group));
      } catch (Exception e) {
        log.error(
            "Notification trigger {} processing failed for {} {}",
            trigger.id(),
            entry.getResourceType(),
            entityId,
            e);
      }
    }
  }

  private void recordEvents(
      ResolvedNotificationTrigger trigger,
      NotificationResourceCatalog entry,
      String entityId,
      NotificationTriggerEventType eventType,
      String message) {
    List<NotificationEventRecord> records =
        trigger.recipientUserIds().stream()
            .map(
                userId -> {
                  NotificationEventRecord record = new NotificationEventRecord();
                  NotificationTrigger triggerReference = new NotificationTrigger();
                  triggerReference.setId(trigger.id());
                  record.setTrigger(triggerReference);
                  User userReference = new User();
                  userReference.setId(userId);
                  record.setUser(userReference);
                  record.setEventType(eventType);
                  record.setMessage(message);
                  record.setResourceTypeValue(entry.getResourceType());
                  record.setResourceId(entityId);
                  record.setTenant(new Tenant(trigger.tenantId()));
                  return record;
                })
            .toList();
    notificationEventRecordRepository.saveAll(records);
  }

  private String buildMessage(
      NotificationResourceCatalog entry, NotificationTriggerEventType eventType, String label) {
    String operation =
        switch (eventType) {
          case CREATE -> "created";
          case UPDATE -> "updated";
          case DELETE -> "deleted";
          case SCORE_DEGRADATION -> "score degraded";
        };
    String resourceLabel = entry.getResourceType().name().toLowerCase().replace('_', ' ');
    return "[" + resourceLabel + "] " + label + " " + operation;
  }
}
