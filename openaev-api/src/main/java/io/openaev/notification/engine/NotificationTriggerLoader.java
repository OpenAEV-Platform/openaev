package io.openaev.notification.engine;

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
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads notification triggers cross-tenant for the engine (trigger matching and digest evaluation).
 * The Hibernate {@code tenantFilter} is enabled with the thread's tenant by {@code
 * HibernateFilterTransactionAspect} on every transactional method, so engine loads - which must see
 * every tenant's triggers - explicitly disable it and resolve triggers to detached records inside
 * the transaction.
 */
@Service
@RequiredArgsConstructor
public class NotificationTriggerLoader {

  private final NotificationTriggerRepository notificationTriggerRepository;
  private final NotificationEventRecordRepository notificationEventRecordRepository;
  private final EntityManager entityManager;

  @Transactional(readOnly = true)
  public List<ResolvedNotificationTrigger> loadEnabledTriggers(NotificationTriggerType type) {
    // Triggers must be found cross-tenant: the engine dispatches for all tenants
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    return notificationTriggerRepository.findAllByTypeAndEnabledTrue(type).stream()
        .map(ResolvedNotificationTrigger::from)
        .toList();
  }

  /**
   * Loads the outbox events of the given live triggers over a time window (cross-tenant), with the
   * associations used after the session closes (trigger name, user id) initialized.
   */
  @Transactional(readOnly = true)
  public List<NotificationEventRecord> loadEventsWindow(
      List<String> triggerIds, Instant from, Instant to) {
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    List<NotificationEventRecord> events =
        notificationEventRecordRepository.findAllByTriggerIdsAndWindow(triggerIds, from, to);
    events.forEach(
        event -> {
          event.getTrigger().getName();
          event.getUser().getId();
        });
    return events;
  }
}
