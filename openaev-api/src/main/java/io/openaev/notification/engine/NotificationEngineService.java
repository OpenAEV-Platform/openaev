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
import io.openaev.service.stream.StreamVisibility;
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
 * <p>Built on top of that RBAC/grant check, a recipient also needs the live event's marking
 * clearance when the resource carries any ({@link StreamVisibility}, resolved from the same payload
 * the activity stream reads): a trigger never reveals an object whose marking a reader is not
 * cleared for, the same way the stream itself never shows it. Unlike the RBAC check, this is
 * resolved only once per live event, not re-checked by the digest (see {@link
 * NotificationDigestService}).
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
  private final MarkingClearanceCacheManager markingClearanceCacheManager;

  /**
   * Processes one entity lifecycle event of a resource type that never carries markings - the
   * event's visibility is unfiltered. For one that may, use {@link #handleEvent(
   * NotificationResourceCatalog, String, String, NotificationTriggerEventType, String, Supplier)}.
   */
  public void handleEvent(
      NotificationResourceCatalog entry,
      String entityId,
      String entityTenantId,
      NotificationTriggerEventType eventType,
      String label) {
    handleEvent(
        entry, entityId, entityTenantId, eventType, label, () -> StreamVisibility.UNFILTERED);
  }

  /**
   * Processes one entity lifecycle event.
   *
   * @param entry catalog entry of the entity's resource type
   * @param entityId id of the affected entity
   * @param entityTenantId tenant of the affected entity (null when not tenant-scoped)
   * @param eventType lifecycle operation
   * @param label human-readable entity label used in notification messages
   * @param visibilitySupplier the event's marking visibility, computed from the same payload the
   *     activity stream uses - a supplier, not a value, so a caller with no live trigger for this
   *     resource type or event never pays for it (see {@link #handleEventWithMessage})
   */
  public void handleEvent(
      NotificationResourceCatalog entry,
      String entityId,
      String entityTenantId,
      NotificationTriggerEventType eventType,
      String label,
      Supplier<StreamVisibility> visibilitySupplier) {
    handleEventWithMessage(
        entry,
        entityId,
        entityTenantId,
        eventType,
        buildMessage(entry, eventType, label),
        visibilitySupplier);
  }

  /**
   * Processes one event with a pre-built notification message, of a resource type that never
   * carries markings. For one that may, use {@link #handleEventWithMessage(
   * NotificationResourceCatalog, String, String, NotificationTriggerEventType, String, Supplier)}.
   */
  public void handleEventWithMessage(
      NotificationResourceCatalog entry,
      String entityId,
      String entityTenantId,
      NotificationTriggerEventType eventType,
      String message) {
    handleEventWithMessage(
        entry, entityId, entityTenantId, eventType, message, () -> StreamVisibility.UNFILTERED);
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
      Supplier<StreamVisibility> visibilitySupplier) {
    handleEventWithMessage(
        entry,
        entityId,
        entityTenantId,
        eventType,
        NotificationMessage.of(message),
        visibilitySupplier);
  }

  /**
   * Processes one event whose message names related objects, of a resource type that never carries
   * markings. For one that may, use {@link #handleEventWithMessage(NotificationResourceCatalog,
   * String, String, NotificationTriggerEventType, NotificationMessage, Supplier)}.
   */
  public void handleEventWithMessage(
      NotificationResourceCatalog entry,
      String entityId,
      String entityTenantId,
      NotificationTriggerEventType eventType,
      NotificationMessage message) {
    handleEventWithMessage(
        entry, entityId, entityTenantId, eventType, message, () -> StreamVisibility.UNFILTERED);
  }

  /**
   * Processes one event whose message names related objects: each recipient gets the message with
   * the names of the related objects it cannot read replaced by "Restricted".
   *
   * @param visibilitySupplier the event's marking visibility (see {@link #handleEvent(
   *     NotificationResourceCatalog, String, String, NotificationTriggerEventType, String,
   *     Supplier)}), applied on top of the RBAC-cleared readers {@link NotificationRecipientFilter}
   *     returns
   */
  public void handleEventWithMessage(
      NotificationResourceCatalog entry,
      String entityId,
      String entityTenantId,
      NotificationTriggerEventType eventType,
      NotificationMessage message,
      Supplier<StreamVisibility> visibilitySupplier) {
    List<ResolvedNotificationTrigger> triggers =
        triggerCacheService.getLiveTriggers(entry.getResourceType());
    if (triggers.isEmpty()) {
      return;
    }
    // Resolved at most once per event, lazily: only once a trigger's event-type and tenant match,
    // and reused for every later trigger of the same event (they all share entityTenantId, by the
    // tenant check below). Outside tenantTx.execute() on purpose: StreamVisibilityService opens
    // its own top-level transaction and refuses to run inside one already active.
    StreamVisibility[] visibilityHolder = new StreamVisibility[1];
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
        if (visibilityHolder[0] == null) {
          visibilityHolder[0] = visibilitySupplier.get();
        }
        StreamVisibility visibility = visibilityHolder[0];
        inTriggerScope(
            trigger,
            () -> {
              if (!matchingService.matches(trigger, entry, entityId)) {
                return List.of();
              }
              List<String> readers = recipientFilter.readersOf(trigger, entry, entityId);
              List<String> cleared = narrowByClearance(trigger, readers, visibility);
              return recordRendered(trigger, cleared, entry, entityId, eventType, message);
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

  /**
   * Narrows RBAC-cleared {@code readers} (see {@link NotificationRecipientFilter}) to those the
   * event's {@link StreamVisibility} also shows - unchanged when the visibility is unfiltered (the
   * MARKING preview feature off, assets not marking-active, or the resource carries none, all
   * folded into {@link StreamVisibility#isUnfiltered()} by the supplier the caller built). Resolved
   * once, before both the outbox write and the live dispatch use it: a recipient never cleared for
   * this event gets neither a {@link #recordEvents} row nor the live notification, so a later
   * digest built from that outbox never has to re-check it.
   */
  private List<String> narrowByClearance(
      ResolvedNotificationTrigger trigger, List<String> readers, StreamVisibility visibility) {
    if (visibility.isUnfiltered()) {
      return readers;
    }
    return readers.stream()
        .filter(
            userId ->
                visibility.visibleUnder(
                    markingClearanceCacheManager.findClearance(userId, trigger.tenantId())))
        .toList();
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
    return "[" + resourceLabel(entry.getResourceType()) + "] " + label + " " + eventType.getLabel();
  }
}
