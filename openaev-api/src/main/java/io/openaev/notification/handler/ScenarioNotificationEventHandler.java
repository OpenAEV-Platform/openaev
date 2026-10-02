package io.openaev.notification.handler;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.expectation.ExpectationType;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.scenario.service.ScenarioStatisticService;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Detects scenario score degradations between the two most recent finished simulations and, when
 * one occurs, fires a {@code SCORE_DEGRADATION} event through the notifications engine (successor
 * of the legacy {@code NotificationRule} DIFFERENCE email).
 */
@Component
@RequiredArgsConstructor
public class ScenarioNotificationEventHandler implements NotificationEventHandler {
  private final EntityManager entityManager;
  private final ExerciseService exerciseService;
  private final NotificationEngineService notificationEngineService;
  private final TenantScopedTransaction tenantTx;

  /** Outcome of the detection phase; {@code null} stands for "no degradation to report". */
  private record Degradation(String scenarioId, String tenantId, String message) {}

  @Override
  public void handle(NotificationEvent event) {
    if (!NotificationEventType.SIMULATION_COMPLETED.equals(event.getEventType())) {
      return;
    }
    // Two phases, deliberately not one transaction. Detection needs a transaction and a tenant
    // scope to read the simulations; the engine then opens its OWN scoped transaction per trigger
    // and TenantScopedTransaction.execute() refuses to run inside an active one. So the detection
    // transaction must be closed before the engine is called - the same contract the CRUD path
    // gets for free from @TransactionalEventListener (it runs after commit).
    Degradation degradation = tenantTx.execute(TxCtx.allTenants(), () -> detect(event));
    if (degradation == null) {
      return;
    }
    notificationEngineService.handleEventWithMessage(
        NotificationResourceCatalog.SCENARIO,
        degradation.scenarioId(),
        degradation.tenantId(),
        NotificationTriggerEventType.SCORE_DEGRADATION,
        degradation.message());
  }

  /** Compares the two most recent finished simulations of the scenario. */
  private Degradation detect(NotificationEvent event) {
    // Disable tenant filter — this handler runs cross-tenant
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    // get the last 2 simulations
    Exercise lastSimulation =
        exerciseService.previousFinishedSimulation(event.getResourceId(), event.getTimestamp());
    if (lastSimulation == null || lastSimulation.getEnd().isEmpty()) {
      return null;
    }
    Exercise secondLastSimulation =
        exerciseService.previousFinishedSimulation(
            event.getResourceId(), lastSimulation.getEnd().get());
    if (secondLastSimulation == null) {
      return null;
    }

    // create map with the results to facilitate the computing of the score difference
    // TODO update exerciseService to return a map with result
    Map<ExpectationType, ExpectationResultsByType> lastSimulationResultsMap =
        exerciseService.getGlobalResults(lastSimulation.getId()).stream()
            .collect(Collectors.toMap(ExpectationResultsByType::type, Function.identity()));
    Map<ExpectationType, ExpectationResultsByType> secondLastSimulationResultsMap =
        exerciseService.getGlobalResults(secondLastSimulation.getId()).stream()
            .collect(Collectors.toMap(ExpectationResultsByType::type, Function.identity()));

    if (!exerciseService.isThereAScoreDegradation(
        lastSimulationResultsMap, secondLastSimulationResultsMap)) {
      return null;
    }
    Scenario scenario = lastSimulation.getScenario();
    String tenantId = scenario.getTenant() != null ? scenario.getTenant().getId() : null;
    if (tenantId == null) {
      // The engine drops tenant-less events fail-closed anyway; bail out now.
      return null;
    }
    return new Degradation(
        scenario.getId(),
        tenantId,
        buildDegradationMessage(
            scenario,
            lastSimulation,
            secondLastSimulation,
            lastSimulationResultsMap,
            secondLastSimulationResultsMap));
  }

  private String buildDegradationMessage(
      @NotNull final Scenario scenario,
      @NotNull final Exercise lastSimulation,
      @NotNull final Exercise secondLastSimulation,
      @NotNull final Map<ExpectationType, ExpectationResultsByType> lastSimulationResultsMap,
      @NotNull
          final Map<ExpectationType, ExpectationResultsByType> secondLastSimulationResultsMap) {
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.systemDefault());

    float lastPrevention =
        getRoundedPercentageSafe(lastSimulationResultsMap.get(ExpectationType.PREVENTION));
    float lastDetection =
        getRoundedPercentageSafe(lastSimulationResultsMap.get(ExpectationType.DETECTION));
    float previousPrevention =
        getRoundedPercentageSafe(secondLastSimulationResultsMap.get(ExpectationType.PREVENTION));
    float previousDetection =
        getRoundedPercentageSafe(secondLastSimulationResultsMap.get(ExpectationType.DETECTION));

    return String.format(
        Locale.ROOT,
        "[scenario] %s: score degradation detected - prevention %.0f%% -> %.0f%%, detection"
            + " %.0f%% -> %.0f%% (previous simulation %s, new simulation %s)",
        scenario.getName(),
        previousPrevention,
        lastPrevention,
        previousDetection,
        lastDetection,
        secondLastSimulation.getEnd().map(formatter::format).orElse("NA"),
        lastSimulation.getEnd().map(formatter::format).orElse("NA"));
  }

  /**
   * Returns 0 if the expectation type has no results (null), avoiding NPE in getRoundedPercentage.
   */
  private static float getRoundedPercentageSafe(
      final ExpectationResultsByType expectationResultsByType) {
    if (expectationResultsByType == null) {
      return 0f;
    }
    return ScenarioStatisticService.getRoundedPercentage(expectationResultsByType);
  }
}
