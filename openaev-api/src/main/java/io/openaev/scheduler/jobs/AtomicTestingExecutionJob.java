package io.openaev.scheduler.jobs;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.InjectRepository;
import io.openaev.service.AtomicTestingService;
import io.openaev.service.period.RecurrenceService;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * Minutely job driving recurring atomic testings, closely modeled on {@link ScenarioExecutionJob}.
 * When an atomic testing has a recurrence whose next occurrence is one minute away, the job
 * relaunches it using the existing relaunch mechanics (duplicate content + queue new + delete old),
 * carrying the recurrence onto the new inject. Recurrences whose end date has passed (or whose next
 * occurrence would fall after the end date) are self-cleared.
 *
 * <p>Background transactions open through {@link TenantScopedTransaction}, never
 * {@code @Transactional} (enforced by the tenant background-transaction ArchUnit rules): the
 * cross-tenant candidate selection carries the {@code allTenants} intention, then each mutation
 * opens its own transaction scoped to the inject's tenant, so one failing tenant cannot poison the
 * others.
 */
@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
@Slf4j
public class AtomicTestingExecutionJob implements Job {

  private final AtomicTestingService atomicTestingService;
  private final RecurrenceService recurrenceService;
  private final InjectRepository injectRepository;
  private final TenantScopedTransaction tenantTx;

  /** A due occurrence, reduced to the ids needed to reopen a tenant-scoped transaction. */
  private record DueRelaunch(String injectId, String tenantId) {}

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    relaunchScheduledAtomicTestings();
    cleanOutdatedRecurringAtomicTestings();
  }

  private void relaunchScheduledAtomicTestings() {
    Instant now = Instant.now();
    // Cross-tenant read: select the due occurrences, keeping only ids so each relaunch below can
    // reopen its own top-level transaction scoped to the inject's tenant.
    List<DueRelaunch> dueRelaunches =
        tenantTx.execute(TxCtx.allTenants(), () -> findDueAtomicTestings(now));
    dueRelaunches.forEach(this::relaunchInTenant);
  }

  private List<DueRelaunch> findDueAtomicTestings(Instant now) {
    // Find each atomic testing with a recurrence where now is between start and end date
    List<Inject> injects = this.atomicTestingService.recurringAtomicTestings(now);
    // Filter on valid recurrence -> next occurrence is in 1 minute (same convention as scenarios:
    // the relaunch queues the inject which is picked up by the minutely InjectsExecutionJob, so the
    // actual execution lands on the occurrence)
    return injects.stream()
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
        .map(inject -> new DueRelaunch(inject.getId(), inject.getTenant().getId()))
        .toList();
  }

  private void relaunchInTenant(DueRelaunch due) {
    try {
      // TenantContext feeds the Hibernate filter aspect on the joined @Transactional service.
      TenantContext.setCurrentTenant(due.tenantId());
      // No Enterprise executor gate here: scheduled execution never re-gates at run time,
      // matching scenario scheduled execution.
      tenantTx.execute(
          TxCtx.forTenant(due.tenantId()),
          () -> this.atomicTestingService.relaunch(due.injectId(), false));
    } catch (Exception e) {
      log.error(
          "Failed to relaunch scheduled atomic testing {}: {}", due.injectId(), e.getMessage(), e);
    } finally {
      TenantContext.clearCurrentTenant();
    }
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
    Instant now = Instant.now();
    // Cross-tenant read: gather the outdated recurrences per tenant, then clear each tenant's
    // batch in its own tenant-scoped transaction.
    Map<String, List<String>> outdatedIdsByTenant =
        tenantTx.execute(
            TxCtx.allTenants(),
            () ->
                this.atomicTestingService.potentialOutdatedRecurringAtomicTestings(now).stream()
                    .filter(this::isRecurrenceOutdated)
                    .collect(
                        groupingBy(
                            inject -> inject.getTenant().getId(),
                            mapping(Inject::getId, toList()))));
    outdatedIdsByTenant.forEach(this::clearRecurrencesInTenant);
  }

  private void clearRecurrencesInTenant(String tenantId, List<String> injectIds) {
    try {
      TenantContext.setCurrentTenant(tenantId);
      tenantTx.execute(
          TxCtx.forTenant(tenantId),
          () -> {
            List<Inject> injects = this.injectRepository.findAllById(injectIds);
            injects.forEach(
                inject -> {
                  inject.setRecurrence(null);
                  inject.setRecurrenceStart(null);
                  inject.setRecurrenceEnd(null);
                });
            if (!injects.isEmpty()) {
              this.atomicTestingService.updateInjects(injects);
            }
          });
    } catch (Exception e) {
      log.error(
          "Failed to clear outdated recurrences for tenant {}: {}", tenantId, e.getMessage(), e);
    } finally {
      TenantContext.clearCurrentTenant();
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
