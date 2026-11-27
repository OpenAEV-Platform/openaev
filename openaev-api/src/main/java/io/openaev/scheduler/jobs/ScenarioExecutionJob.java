package io.openaev.scheduler.jobs;

import static io.openaev.context.TxCtx.forTenant;
import static io.openaev.database.specification.ExerciseSpecification.recurringInstanceNotStarted;

import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioRecurrenceService;
import io.openaev.service.scenario.ScenarioService;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class ScenarioExecutionJob implements Job {

  private final ScenarioService scenarioService;
  private final ScenarioRecurrenceService scenarioRecurrenceService;
  private final ExerciseRepository exerciseRepository;
  private final ScenarioToExerciseService scenarioToExerciseService;
  private final WorkflowService workflowService;
  private final TenantScopedTransaction tenantTx;
  private final TenantRepository tenantRepository;

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    Instant now = Instant.now();
    tenantRepository
        .findAllIdsByDeletedAtIsNull()
        .forEach(
            tenantId -> {
              createExercisesFromScenariosAndByTenant(tenantId, now);
              cleanOutdatedRecurringScenario(tenantId);
            });
  }

  private void createExercisesFromScenariosAndByTenant(String tenantId, Instant now) {
    executeInTenant(
        tenantId,
        () -> {
          // Find each scenario with cron where now is between start and end date
          List<Scenario> scenarios = this.scenarioService.recurringScenarios(now);
          // Filter on valid cron scenario -> Start date on cron is in 1 minute
          List<Scenario> validScenarios =
              scenarios.stream()
                  .filter(
                      scenario -> {
                        Optional<Instant> nextOccurrence =
                            scenarioRecurrenceService.getNextExecutionTime(scenario, now);
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
                  .toList();
          // Check if a simulation link to this scenario already exists
          // Retrieve simulations not started, link to a scenario
          List<String> alreadyExistIds =
              this.exerciseRepository.findAll(recurringInstanceNotStarted()).stream()
                  .map(Exercise::getScenario)
                  .map(Scenario::getId)
                  .toList();
          // Filter scenarios with this results
          validScenarios.stream()
              .filter(scenario -> !alreadyExistIds.contains(scenario.getId()))
              // Time-based scenarios stay scheduled and are auto-started later.
              // Chained scenarios only provision their simulation template here; the workflow run
              // is created when the scheduled simulation is auto-started.
              .forEach(scenario -> createScheduledExercise(scenario, now));
        });
  }

  private void createScheduledExercise(Scenario scenario, Instant now) {
    Instant start = scenarioRecurrenceService.getNextExecutionTime(scenario, now).orElse(now);
    Exercise exercise =
        this.scenarioToExerciseService.toExercise(
            scenario, start, false, scenario.getScheduledBy());
    // Chained scenarios need the workflow template now; the workflow run is created later when
    // the scheduled exercise is auto-started.
    provisionChainedWorkflowTemplateIfNeeded(scenario.getId(), exercise);
  }

  private void provisionChainedWorkflowTemplateIfNeeded(String scenarioId, Exercise exercise) {
    if (!this.workflowService.isScenarioChaining(scenarioId)) {
      return;
    }
    try {
      this.workflowService.provisionSimulationTemplateWorkflow(scenarioId, exercise);
    } catch (ChainingException e) {
      throw new IllegalStateException("Could not provision chained scenario " + scenarioId, e);
    }
  }

  /**
   * Runs one step of the pass in its tenant's transaction. A failure rolls that step back, is
   * logged, and ends there: the job raises nothing, and the other steps of this tenant and the
   * other tenants run all the same.
   */
  private void executeInTenant(@NotNull final String tenantId, @NotNull final Runnable work) {
    try {
      tenantTx.execute(forTenant(tenantId), work);
    } catch (RuntimeException e) {
      log.error("Scheduled scenario pass failed for tenant {}: {}", tenantId, e.getMessage(), e);
    }
  }

  private void cleanOutdatedRecurringScenario(String tenantId) {
    executeInTenant(
        tenantId,
        () -> {
          // Find each scenario with cron is outdated:
          List<Scenario> scenarios =
              this.scenarioService.potentialOutdatedRecurringScenario(Instant.now());
          List<Scenario> validScenarios =
              scenarios.stream().filter(this::isScenarioOutdated).toList();

          // Remove recurring setup
          validScenarios.forEach(
              s -> {
                s.setRecurrenceStart(null);
                s.setRecurrenceEnd(null);
                s.setRecurrence(null);
              });
          // Save it
          if (!validScenarios.isEmpty()) this.scenarioService.updateScenarios(validScenarios);
        });
  }

  private boolean isScenarioOutdated(@NotNull final Scenario scenario) {
    if (scenario.getRecurrenceEnd() == null) {
      return false;
    }
    // End date is passed
    if (scenario.getRecurrenceEnd().isBefore(Instant.now())) {
      return true;
    }

    // There are no next execution -> example: end date is tomorrow at 1AM and execution cron is at
    // 6AM and it's 6PM
    Instant nextExecution =
        scenarioRecurrenceService
            .getNextExecutionTime(scenario, Instant.now())
            .orElse(Instant.now());
    return nextExecution.isAfter(scenario.getRecurrenceEnd());
  }
}
