package io.openaev.rest.reporting.service;

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
 * engine's {@code NotificationTriggerLoader}. The Hibernate {@code tenantFilter} is enabled with
 * the thread's tenant by {@code HibernateFilterTransactionAspect} on every transactional method, so
 * engine loads - which must see every tenant's schedules - explicitly disable it and return
 * schedules with the associations needed after the session closes fully initialized (fetch joins).
 */
@Service
@RequiredArgsConstructor
public class ReportingScheduleLoader {

  private final ReportingScheduleRepository reportingScheduleRepository;
  private final EntityManager entityManager;

  /**
   * Loads every enabled schedule of every tenant, with reporting, owner, tenant and recipient users
   * initialized so callers can use the detached entities outside the session.
   */
  @Transactional(readOnly = true)
  public List<ReportingSchedule> loadEnabledSchedules() {
    // Schedules must be found cross-tenant: the engine fires for all tenants
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    return reportingScheduleRepository.findAllEnabledForScheduling();
  }

  /**
   * Persists the last-run marker of a schedule (double-fire guard). Uses a managed reload by id
   * (unaffected by the tenant filter) so only the {@code lastRunAt} column is updated.
   */
  @Transactional
  public void markLastRun(String scheduleId, Instant lastRunAt) {
    reportingScheduleRepository
        .findById(scheduleId)
        .ifPresent(schedule -> schedule.setLastRunAt(lastRunAt));
  }
}
