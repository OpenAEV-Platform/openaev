package io.openaev.scheduler.jobs;

import static io.openaev.database.specification.ExerciseSpecification.recurringInstanceNotStarted;

import io.openaev.aop.LogExecutionTime;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.period.CronService;
import io.openaev.service.scenario.ScenarioRecurrenceService;
import io.openaev.service.scenario.ScenarioService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class ScenarioExecutionJob implements Job {

  private final ScenarioService scenarioService;
  private final ScenarioRecurrenceService scenarioRecurrenceService;
  private final ExerciseRepository exerciseRepository;
  private final ScenarioToExerciseService scenarioToExerciseService;
  private final CronService cronService;

  @Override
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    createExercisesFromScenarios();
    cleanOutdatedRecurringScenario();
  }

  private void createExercisesFromScenarios() {
    Instant now = Instant.now();
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
        // Create simulation with start date provided by cron
        .forEach(
            scenario ->
                this.scenarioToExerciseService.toExercise(
                    scenario,
                    scenarioRecurrenceService.getNextExecutionTime(scenario, now).orElse(now),
                    false));
  }

  private void cleanOutdatedRecurringScenario() {
    // Find each scenario with cron is outdated:
    List<Scenario> scenarios =
        this.scenarioService.potentialOutdatedRecurringScenario(Instant.now());
    List<Scenario> validScenarios = scenarios.stream().filter(this::isScenarioOutdated).toList();

    // Remove recurring setup
    validScenarios.forEach(
        s -> {
          s.setRecurrenceStart(null);
          s.setRecurrenceEnd(null);
          s.setRecurrence(null);
        });
    // Save it
    this.scenarioService.updateScenarios(scenarios);
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
