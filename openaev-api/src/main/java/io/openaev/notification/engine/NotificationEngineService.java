package io.openaev.notification.engine;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Live stage of the notifications engine: for a single entity event, evaluates every live trigger
 * watching that resource type, records an outbox {@link NotificationEventRecord} per (trigger,
 * user) match (the digest source) and dispatches the trigger's notifiers immediately.
 *
 * <p>Only the recipients who can read the event's object are recorded and notified ({@link
 * NotificationRecipientFilter}): a trigger on a resource type never reveals an object of that type
 * to a recipient the API would refuse it to. The digest replays the recorded rows, so it inherits
 * the same filter, and re-checks each row at send time.
 *
 * <p>A message is rendered per recipient ({@link NotificationMessage}): a related object the
 * recipient cannot read is named "Restricted", the event is never dropped for it. A recipient who
 * loses the object itself (the object deleted, a grant removed) gets a delete-type event computed
 * from the readers captured before the change ({@link NotificationReadersSnapshot}).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationEngineService {

  private final NotificationTriggerCacheService triggerCacheService;
  private final NotificationMatchingService matchingService;
  private final NotificationDispatchService dispatchService;
  private final NotificationRecipientFilter recipientFilter;
  private final NotificationEventRecordRepository notificationEventRecordRepository;
  private final TenantScopedTransaction tenantTx;

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
    handleEventWithMessage(
        entry, entityId, entityTenantId, eventType, NotificationMessage.of(message));
  }

  /**
   * Processes one event whose message names related objects: each recipient gets the message with
   * the names of the related objects it cannot read replaced by "Restricted".
   */
  public void handleEventWithMessage(
      NotificationResourceCatalog entry,
      String entityId,
      String entityTenantId,
      NotificationTriggerEventType eventType,
      NotificationMessage message) {
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
        inTriggerScope(
            trigger,
            () -> {
              if (!matchingService.matches(trigger, entry, entityId)) {
                return List.of();
              }
              List<String> readers = recipientFilter.readersOf(trigger, entry, entityId);
              return recordRendered(trigger, readers, entry, entityId, eventType, message);
            });
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
   * Whether a live trigger of the tenant listens to this event type on this resource type. Lets a
   * handler skip its own reads when nobody would be notified, so an instance without such a trigger
   * does no extra work.
   */
  public boolean hasLiveTrigger(
      NotificationResourceCatalog entry, String tenantId, NotificationTriggerEventType eventType) {
    if (tenantId == null) {
      return false;
    }
    return triggerCacheService.getLiveTriggers(entry.getResourceType()).stream()
        .anyMatch(
            trigger ->
                trigger.eventTypes().contains(eventType) && tenantId.equals(trigger.tenantId()));
  }

  /**
   * Delivers what a change took away, from the readers captured before it, after its commit.
   *
   * <ul>
   *   <li>A deletion: every captured reader receives the deleted event, with the name it read.
   *   <li>An access loss: each captured reader who no longer reads the object receives a
   *       delete-type "no longer visible" event, the name masked; a reader who still reads it gets
   *       nothing.
   * </ul>
   */
  public void deliverSnapshot(NotificationReadersSnapshot snapshot) {
    NotificationResourceCatalog entry = snapshot.entry();
    for (NotificationReadersSnapshot.CapturedReaders captured : snapshot.triggers()) {
      ResolvedNotificationTrigger trigger = captured.trigger();
      try {
        if (captured.readers().isEmpty()
            || !trigger.eventTypes().contains(NotificationTriggerEventType.DELETE)) {
          continue;
        }
        inTriggerScope(
            trigger,
            () -> {
              if (snapshot.kind() == NotificationReadersSnapshot.Kind.DELETION) {
                return recordRendered(
                    trigger,
                    captured.readers(),
                    entry,
                    snapshot.entityId(),
                    NotificationTriggerEventType.DELETE,
                    NotificationMessage.of(
                        buildMessage(
                            entry, NotificationTriggerEventType.DELETE, snapshot.label())));
              }
              List<String> lost =
                  captured.readers().stream()
                      .filter(
                          userId ->
                              !recipientFilter.canRead(
                                  trigger.tenantId(), userId, entry, snapshot.entityId()))
                      .toList();
              return recordRendered(
                  trigger,
                  lost,
                  entry,
                  snapshot.entityId(),
                  NotificationTriggerEventType.DELETE,
                  NotificationMessage.of(noLongerVisibleMessage(entry.getResourceType())));
            });
      } catch (Exception e) {
        log.error(
            "Notification trigger {} {} delivery failed for {} {}",
            trigger.id(),
            snapshot.kind(),
            entry.getResourceType(),
            snapshot.entityId(),
            e);
      }
    }
  }

  /** One live dispatch: the recipients who get the same rendering, and what they get. */
  private record Dispatch(List<String> users, List<NotificationContent.Group> groups) {}

  /**
   * Runs {@code prepare} and dispatches what it returns in the trigger's scope, the only place the
   * live stage opens one: TenantContext keeps v1 paths correct on the async thread, the
   * TenantScopedTransaction / TxCtx scopes v2-active reads, and every dispatch is made in the work
   * of that scope.
   */
  private void inTriggerScope(
      ResolvedNotificationTrigger trigger, Supplier<List<Dispatch>> prepare) {
    TenantContext.setCurrentTenant(trigger.tenantId());
    try {
      tenantTx.execute(
          TxCtx.forTenant(trigger.tenantId()),
          () -> {
            for (Dispatch dispatch : prepare.get()) {
              dispatchService.dispatch(
                  trigger, NotificationTriggerType.LIVE, dispatch.users(), dispatch.groups());
            }
          });
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  /**
   * Records one event for {@code readers}, the message rendered for each of them, and returns the
   * dispatches to make: readers who get the same rendering share one. Must run in the trigger's
   * scope.
   */
  private List<Dispatch> recordRendered(
      ResolvedNotificationTrigger trigger,
      List<String> readers,
      NotificationResourceCatalog entry,
      String entityId,
      NotificationTriggerEventType eventType,
      NotificationMessage message) {
    if (readers.isEmpty()) {
      return List.of();
    }
    Map<String, List<String>> readersByRendering = new LinkedHashMap<>();
    for (String userId : readers) {
      String rendered =
          message.render(
              reference ->
                  recipientFilter.canRead(
                      trigger.tenantId(), userId, reference.entry(), reference.id()));
      readersByRendering.computeIfAbsent(rendered, key -> new java.util.ArrayList<>()).add(userId);
    }
    List<Dispatch> dispatches = new java.util.ArrayList<>();
    readersByRendering.forEach(
        (rendered, users) -> {
          recordEvents(trigger, users, entry, entityId, eventType, rendered);
          NotificationContent.Group group =
              new NotificationContent.Group(
                  trigger.name(),
                  List.of(
                      new NotificationContent.Event(
                          eventType, rendered, entry.getResourceType(), entityId)));
          dispatches.add(new Dispatch(users, List.of(group)));
        });
    return dispatches;
  }

  /** Suffix of the delete-type event a recipient gets when it loses read access to an object. */
  static final String NO_LONGER_VISIBLE = "is no longer visible";

  /** The message of an object the recipient no longer reads: its name is never shown. */
  static String noLongerVisibleMessage(io.openaev.database.model.ResourceType resourceType) {
    return "["
        + resourceLabel(resourceType)
        + "] "
        + NotificationMessage.RESTRICTED
        + " "
        + NO_LONGER_VISIBLE;
  }

  /** A deleted-object message with the name masked, for a digest row the recipient cannot read. */
  static String maskedDeletedMessage(io.openaev.database.model.ResourceType resourceType) {
    return "[" + resourceLabel(resourceType) + "] " + NotificationMessage.RESTRICTED + " deleted";
  }

  private static String resourceLabel(io.openaev.database.model.ResourceType resourceType) {
    return resourceType.name().toLowerCase().replace('_', ' ');
  }

  private void recordEvents(
      ResolvedNotificationTrigger trigger,
      List<String> readers,
      NotificationResourceCatalog entry,
      String entityId,
      NotificationTriggerEventType eventType,
      String message) {
    List<NotificationEventRecord> records =
        readers.stream()
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
          case CRITICAL_ASSET_REACHED -> "critical asset reached";
        };
    return "[" + resourceLabel(entry.getResourceType()) + "] " + label + " " + operation;
  }
}
