package io.openaev.notification.engine;

import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.NotificationEventRecordRepository;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.stream.StreamVisibility;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Live stage of the notifications engine: for a single entity event, evaluates every live trigger
 * watching that resource type, records an outbox {@link NotificationEventRecord} per (trigger,
 * cleared recipient) match (the digest source) and dispatches the trigger's notifiers immediately.
 * Marking clearance is resolved once per trigger match, before the outbox write, so a recipient
 * never cleared for the event never gets a row recorded for it either.
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
  private final MarkingClearanceCacheManager markingClearanceCacheManager;
  private final PreviewFeatureService previewFeatureService;

  /**
   * Processes one entity lifecycle event.
   *
   * @param entry catalog entry of the entity's resource type
   * @param entityId id of the affected entity
   * @param entityTenantId tenant of the affected entity (null when not tenant-scoped)
   * @param eventType lifecycle operation
   * @param label human-readable entity label used in notification messages
   * @param visibility the event's marking visibility ({@code StreamVisibility.UNFILTERED} for a
   *     resource type that carries no markings), computed once by the caller from the same payload
   *     the activity stream uses
   */
  public void handleEvent(
      NotificationResourceCatalog entry,
      String entityId,
      String entityTenantId,
      NotificationTriggerEventType eventType,
      String label,
      StreamVisibility visibility) {
    handleEventWithMessage(
        entry,
        entityId,
        entityTenantId,
        eventType,
        buildMessage(entry, eventType, label),
        visibility);
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
      String message,
      StreamVisibility visibility) {
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
                List<String> recipients = recipientsUnderClearance(trigger, visibility);
                recordEvents(trigger, entry, entityId, eventType, message, recipients);
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

  /**
   * Narrows a trigger's recipients to those the event's {@link StreamVisibility} shows - unchanged
   * when the MARKING preview feature is off or the visibility is unfiltered. The feature check
   * comes first, deliberately: {@code visibility} is already computed by the caller at that point
   * (from the same payload the activity stream reads), so there is nothing to gate a DB lookup for
   * here, but the clearance lookup per recipient is still worth skipping. Resolved once, before
   * both the outbox write and the live dispatch use it: a recipient never cleared for this event
   * gets neither a {@link #recordEvents} row nor the live notification, so a later digest built
   * from that outbox never has to re-check it.
   */
  private List<String> recipientsUnderClearance(
      ResolvedNotificationTrigger trigger, StreamVisibility visibility) {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)
        || visibility.isUnfiltered()) {
      return trigger.recipientUserIds();
    }
    return trigger.recipientUserIds().stream()
        .filter(
            userId ->
                visibility.visibleUnder(
                    markingClearanceCacheManager.findClearance(userId, trigger.tenantId())))
        .toList();
  }

  private void recordEvents(
      ResolvedNotificationTrigger trigger,
      NotificationResourceCatalog entry,
      String entityId,
      NotificationTriggerEventType eventType,
      String message,
      List<String> recipients) {
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
          case SCORE_DEGRADATION -> "score degraded";
          case SYNC_BLOCKED -> "sync blocked";
        };
    String resourceLabel = entry.getResourceType().name().toLowerCase().replace('_', ' ');
    return "[" + resourceLabel + "] " + label + " " + operation;
  }
}
