package io.openaev.notification.engine;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.repository.NotificationEventRecordRepository;
import io.openaev.database.repository.NotificationTriggerRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

/**
 * Loads notification triggers cross-tenant for the engine (trigger matching and digest evaluation).
 *
 * <p>Background-only: both callers - the trigger cache refresh on the async engine thread and the
 * digest job - run outside any request. Transactions are opened through {@link
 * TenantScopedTransaction} under {@link TxCtx#allTenants()}, never {@code @Transactional}: the
 * engine dispatches for every tenant, and a v2-active table read without a scope fails closed and
 * silently returns nothing.
 *
 * <p>{@code notifiers} is v2-active, and a trigger's notifiers are a lazy {@code @ManyToMany}
 * resolved by {@link ResolvedNotificationTrigger#from}. Without the scope below that collection
 * comes back EMPTY, {@code NotificationDispatchService.dispatch} no-ops on it, and every
 * notification stops being delivered with no log and no exception (#7864).
 *
 * <p>The {@code disableFilter} call below is defensive. {@code HibernateFilterTransactionAspect}
 * enables the v1 {@code tenantFilter} from a method-level {@code @Transactional}, which this class
 * no longer carries, so on the current call paths there should be nothing to disable; it is kept so
 * that a future {@code @Transactional} hop on this session cannot silently re-scope these
 * cross-tenant reads of the still-v1 {@code notification_triggers} / {@code notification_events}.
 */
@Service
@RequiredArgsConstructor
public class NotificationTriggerLoader {

  private final NotificationTriggerRepository notificationTriggerRepository;
  private final NotificationEventRecordRepository notificationEventRecordRepository;
  private final EntityManager entityManager;
  private final TenantScopedTransaction tenantTx;

  public List<ResolvedNotificationTrigger> loadEnabledTriggers(NotificationTriggerType type) {
    return tenantTx.execute(
        TxCtx.allTenants(),
        () -> {
          disableV1TenantFilter();
          return notificationTriggerRepository.findAllByTypeAndEnabledTrue(type).stream()
              .map(ResolvedNotificationTrigger::from)
              .toList();
        });
  }

  /**
   * Loads the outbox events of the given live triggers over a time window (cross-tenant), with the
   * associations used after the session closes (trigger name, user id) initialized.
   */
  public List<NotificationEventRecord> loadEventsWindow(
      List<String> triggerIds, Instant from, Instant to) {
    return tenantTx.execute(
        TxCtx.allTenants(),
        () -> {
          disableV1TenantFilter();
          List<NotificationEventRecord> events =
              notificationEventRecordRepository.findAllByTriggerIdsAndWindow(triggerIds, from, to);
          events.forEach(
              event -> {
                event.getTrigger().getName();
                event.getUser().getId();
              });
          return events;
        });
  }

  // No-op when the filter was never enabled; see the class javadoc.
  private void disableV1TenantFilter() {
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
  }
}
