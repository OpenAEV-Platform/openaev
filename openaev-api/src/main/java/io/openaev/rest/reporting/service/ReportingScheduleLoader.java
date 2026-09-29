package io.openaev.rest.reporting.service;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.ReportingSchedule;
import io.openaev.database.repository.ReportingScheduleRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads reporting schedules cross-tenant for the scheduling engine, mirroring the notification
 * engine's {@code NotificationTriggerLoader}. {@code reporting_schedules} is tenant-active: the
 * engine load, which must see every tenant's schedules, opens its own transaction through {@link
 * TenantScopedTransaction} under {@link TxCtx#allTenants()}, never {@code @Transactional} alone -
 * an unscoped read of an active table fails closed and silently returns nothing. The {@code
 * disableFilter} call is defensive, kept so that a future {@code @Transactional} hop on this
 * session cannot silently re-scope this cross-tenant read of the still-v1 tables it joins (owner,
 * recipient users).
 */
@Service
@RequiredArgsConstructor
public class ReportingScheduleLoader {

  private final ReportingScheduleRepository reportingScheduleRepository;
  private final EntityManager entityManager;
  private final TenantScopedTransaction tenantTx;

  /**
   * Loads every enabled schedule of every tenant, with reporting, owner, tenant and recipient users
   * initialized so callers can use the detached entities outside the session.
   */
  public List<ReportingSchedule> loadEnabledSchedules() {
    return tenantTx.execute(
        TxCtx.allTenants(),
        () -> {
          disableV1TenantFilter();
          return reportingScheduleRepository.findAllEnabledForScheduling();
        });
  }

  /**
   * Persists the last-run marker of a schedule (double-fire guard). Uses a managed reload by id.
   * Callers run this inside the primitive scope of the schedule's own tenant (see {@link
   * io.openaev.rest.reporting.service.ReportingScheduleService#executeSchedule}); on its own this
   * method sets no scope.
   */
  @Transactional
  public void markLastRun(String scheduleId, Instant lastRunAt) {
    reportingScheduleRepository
        .findById(scheduleId)
        .ifPresent(schedule -> schedule.setLastRunAt(lastRunAt));
  }

  // No-op when the filter was never enabled; see the class javadoc.
  private void disableV1TenantFilter() {
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
  }
}
