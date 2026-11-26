package io.openaev.notification.engine;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Grant;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.service.StreamService;
import io.openaev.service.notification.GroupAccessRevokedEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Captures, in the changing transaction and before the change, who reads an object a change is
 * about to take away from its readers, so the engine evaluates visibility before and after per
 * recipient as OpenCTI does:
 *
 * <ul>
 *   <li>the deletion of a grant-managed root (scenario, simulation, atomic testing), the case
 *       {@link StreamService#isGrantManagedRootDeletion} names: its grants go with the row, so a
 *       check after commit denies every grant reader;
 *   <li>a grant removed, members removed from a group, a group deleted ({@link
 *       GroupAccessRevokedEvent}).
 * </ul>
 *
 * <p>The capture runs synchronously (a plain {@code @EventListener}) in a nested transaction
 * ({@link TenantScopedTransaction#executeNew}): it reads the committed state, which still holds the
 * grants, and a failure there never marks the caller's transaction rollback-only. The snapshot is
 * then published as an event the engine listener delivers after commit; a rolled back change sends
 * nothing. A capture that fails is logged and dropped (fail closed: no event rather than a wrong
 * one).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationReadersCaptureListener {

  private final NotificationTriggerCacheService triggerCacheService;
  private final NotificationMatchingService matchingService;
  private final NotificationRecipientFilter recipientFilter;
  private final TenantScopedTransaction tenantTx;
  private final ApplicationEventPublisher eventPublisher;

  @EventListener
  public void onEntityEvent(BaseEvent event) {
    try {
      if (event.getInstance() == null || !StreamService.isGrantManagedRootDeletion(event)) {
        return;
      }
      Optional<NotificationResourceCatalog> entry =
          NotificationResourceCatalog.fromEntity(event.getInstance());
      String tenantId = event.getTenantId();
      if (entry.isEmpty() || tenantId == null) {
        return;
      }
      capture(
          NotificationReadersSnapshot.Kind.DELETION,
          entry.get(),
          event.getInstance().getId(),
          tenantId,
          NotificationEngineListener.resolveLabel(event),
          null);
    } catch (Exception e) {
      log.error("Notification readers capture failed for a deletion", e);
    }
  }

  @EventListener
  public void onGroupAccessRevoked(GroupAccessRevokedEvent event) {
    for (GroupAccessRevokedEvent.Target target : event.targets()) {
      try {
        Optional<NotificationResourceCatalog> entry = catalogOf(target.resourceType());
        if (entry.isEmpty()) {
          continue;
        }
        capture(
            NotificationReadersSnapshot.Kind.ACCESS_LOSS,
            entry.get(),
            target.resourceId(),
            event.tenantId(),
            null,
            event.userIds());
      } catch (Exception e) {
        log.error(
            "Notification readers capture failed for {} {}",
            target.resourceType(),
            target.resourceId(),
            e);
      }
    }
  }

  static Optional<NotificationResourceCatalog> catalogOf(Grant.GRANT_RESOURCE_TYPE resourceType) {
    return switch (resourceType) {
      case SCENARIO -> Optional.of(NotificationResourceCatalog.SCENARIO);
      case SIMULATION -> Optional.of(NotificationResourceCatalog.SIMULATION);
      default -> Optional.empty();
    };
  }

  private void capture(
      NotificationReadersSnapshot.Kind kind,
      NotificationResourceCatalog entry,
      String entityId,
      String tenantId,
      String label,
      Collection<String> candidates) {
    List<ResolvedNotificationTrigger> triggers =
        triggerCacheService.getLiveTriggers(entry.getResourceType()).stream()
            .filter(trigger -> tenantId.equals(trigger.tenantId()))
            .filter(trigger -> trigger.eventTypes().contains(NotificationTriggerEventType.DELETE))
            .toList();
    if (triggers.isEmpty()) {
      return;
    }
    List<NotificationReadersSnapshot.CapturedReaders> captured =
        readInTenant(
            tenantId,
            () -> {
              List<NotificationReadersSnapshot.CapturedReaders> result = new ArrayList<>();
              for (ResolvedNotificationTrigger trigger : triggers) {
                if (!matchingService.matches(trigger, entry, entityId)) {
                  continue;
                }
                List<String> readers =
                    candidates == null
                        ? recipientFilter.readersOf(trigger, entry, entityId)
                        : recipientFilter.readersOf(trigger, entry, entityId, candidates);
                if (!readers.isEmpty()) {
                  result.add(new NotificationReadersSnapshot.CapturedReaders(trigger, readers));
                }
              }
              return result;
            });
    if (captured.isEmpty()) {
      return;
    }
    eventPublisher.publishEvent(
        new NotificationReadersSnapshot(kind, entry, entityId, tenantId, label, captured));
  }

  private <T> T readInTenant(String tenantId, java.util.function.Supplier<T> work) {
    TxCtx ctx = TxCtx.forTenant(tenantId);
    return TransactionSynchronizationManager.isActualTransactionActive()
        ? tenantTx.executeNew(ctx, work)
        : tenantTx.execute(ctx, work);
  }
}
