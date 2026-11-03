package io.openaev.notification.engine;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.NotificationEventRecordRepository;
import io.openaev.service.UserService;
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
  private final TenantScopedTransaction tenantTx;
  private final UserService userService;

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
        // Tenant isolation (fail-closed): a trigger only sees events of its own tenant, and
        // events without a resolved tenant are dropped - every catalog entity is tenant-scoped
        // and user events are fanned out per tenant by the listener.
        if (entityTenantId == null || !entityTenantId.equals(trigger.tenantId())) {
          continue;
        }
        // Run with both scopes on the async thread: TenantContext keeps v1 paths correct,
        // TenantScopedTransaction/TX ctx scopes v2-active reads.
        TenantContext.setCurrentTenant(trigger.tenantId());
        try {
          tenantTx.execute(
              TxCtx.forTenant(trigger.tenantId()),
              () -> {
                if (!matchingService.matches(trigger, entry, entityId)) {
                  return;
                }
                List<String> recipients = allowedRecipients(trigger, entry);
                if (recipients.isEmpty()) {
                  return;
                }
                recordEvents(trigger, recipients, entry, entityId, eventType, message);
                NotificationContent.Group group =
                    new NotificationContent.Group(
                        trigger.name(),
                        List.of(
                            new NotificationContent.Event(
                                eventType, message, entry.getResourceType(), entityId)));
                dispatchService.dispatch(
                    trigger, NotificationTriggerType.LIVE, recipients, List.of(group));
              });
        } finally {
          TenantContext.clearCurrentTenant();
        }
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

  private List<String> allowedRecipients(
      ResolvedNotificationTrigger trigger, NotificationResourceCatalog entry) {
    Capability required = entry.requiredCapability();
    if (required == null) {
      return trigger.recipientUserIds();
    }
    return userService.find(trigger.recipientUserIds()).stream()
        .filter(user -> user.isAdmin() || user.getCapabilities().contains(required))
        .map(User::getId)
        .toList();
  }

  private void recordEvents(
      ResolvedNotificationTrigger trigger,
      List<String> recipients,
      NotificationResourceCatalog entry,
      String entityId,
      NotificationTriggerEventType eventType,
      String message) {
    List<NotificationEventRecord> records =
        recipients.stream()
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
          // Sent by PayloadApprovalNotificationService with its own messages, never matched here
          // (triggers cannot subscribe to them).
          case APPROVAL_PENDING -> "pending approval";
          default -> eventType.name().toLowerCase().replace('_', ' ');
        };
    String resourceLabel = entry.getResourceType().name().toLowerCase().replace('_', ' ');
    return "[" + resourceLabel + "] " + label + " " + operation;
  }
}
