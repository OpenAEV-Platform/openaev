package io.openaev.scheduler.jobs;

import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TenantContext;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.service.AtomicTestingService;
import io.openaev.service.period.RecurrenceService;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minutely job driving recurring atomic testings, closely modeled on {@link ScenarioExecutionJob}.
 * When an atomic testing has a recurrence whose next occurrence is one minute away, the job
 * relaunches it using the existing relaunch mechanics (duplicate content + queue new + delete old),
 * carrying the recurrence onto the new inject. Recurrences whose end date has passed (or whose next
 * occurrence would fall after the end date) are self-cleared.
 */
@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
@Slf4j
public class AtomicTestingExecutionJob implements Job {

  private final AtomicTestingService atomicTestingService;
  private final RecurrenceService recurrenceService;
  private final EntityManager entityManager;

  @Override
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    // Disable tenant filter — this job runs cross-tenant
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    relaunchScheduledAtomicTestings();
    cleanOutdatedRecurringAtomicTestings();
  }

  private void relaunchScheduledAtomicTestings() {
    Instant now = Instant.now();
    // Find each atomic testing with a recurrence where now is between start and end date
    List<Inject> injects = this.atomicTestingService.recurringAtomicTestings(now);
    // Filter on valid recurrence -> next occurrence is in 1 minute (same convention as scenarios:
    // the relaunch queues the inject which is picked up by the minutely InjectsExecutionJob, so the
    // actual execution lands on the occurrence)
    List<Inject> validInjects =
        injects.stream()
            .filter(
                inject -> {
                  Optional<Instant> nextOccurrence = getNextExecutionTime(inject, now);
                  if (nextOccurrence.isEmpty()) {
                    return false;
                  }
                  Instant startDate = nextOccurrence.get().minus(1, ChronoUnit.MINUTES);
                  ZonedDateTime startDateMinute =
                      startDate.atZone(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MINUTES);
                  ZonedDateTime nowMinute =
                      now.atZone(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MINUTES);
                  return startDateMinute.equals(nowMinute);
                })
            // Dedup: skip when a run is already queued or in progress for this inject
            .filter(inject -> !isRunInProgress(inject))
            .toList();

    validInjects.forEach(
        inject -> {
          try {
            TenantContext.setCurrentTenant(inject.getTenant().getId());
            // No Enterprise executor gate here: scheduled execution never re-gates at run time,
            // matching scenario scheduled execution.
            this.atomicTestingService.relaunch(inject.getId(), false);
          } catch (Exception e) {
            log.error(
                "Failed to relaunch scheduled atomic testing {}: {}",
                inject.getId(),
                e.getMessage(),
                e);
          } finally {
            TenantContext.clearCurrentTenant();
          }
        });
  }

  private boolean isRunInProgress(@NotNull final Inject inject) {
    return inject
        .getStatus()
        .map(
            status ->
                ExecutionStatus.QUEUING.equals(status.getName())
                    || ExecutionStatus.PENDING.equals(status.getName()))
        .orElse(false);
  }

  private void cleanOutdatedRecurringAtomicTestings() {
    List<Inject> injects =
        this.atomicTestingService.potentialOutdatedRecurringAtomicTestings(Instant.now());
    List<Inject> outdatedInjects = injects.stream().filter(this::isRecurrenceOutdated).toList();

    // Remove recurring setup
    outdatedInjects.forEach(
        inject -> {
          inject.setRecurrence(null);
          inject.setRecurrenceStart(null);
          inject.setRecurrenceEnd(null);
        });
    if (!outdatedInjects.isEmpty()) {
      this.atomicTestingService.updateInjects(outdatedInjects);
    }
  }

  private boolean isRecurrenceOutdated(@NotNull final Inject inject) {
    if (inject.getRecurrenceEnd() == null) {
      return false;
    }
    // End date is passed
    if (inject.getRecurrenceEnd().isBefore(Instant.now())) {
      return true;
    }

    // There is no next execution before the end date -> example: end date is tomorrow at 1AM and
    // execution cron is at 6AM and it's 6PM
    Instant nextExecution = getNextExecutionTime(inject, Instant.now()).orElse(Instant.now());
    return nextExecution.isAfter(inject.getRecurrenceEnd());
  }

  private Optional<Instant> getNextExecutionTime(@NotNull final Inject inject, Instant now) {
    return recurrenceService.getNextOccurrence(
        inject.getRecurrence(), inject.getRecurrenceStart(), now);
  }
}
