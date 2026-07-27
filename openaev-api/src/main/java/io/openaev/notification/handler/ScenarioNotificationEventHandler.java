package io.openaev.notification.handler;

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
import org.springframework.transaction.annotation.Transactional;

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

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void handle(NotificationEvent event) {
    // Disable tenant filter — this handler runs cross-tenant
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    if (NotificationEventType.SIMULATION_COMPLETED.equals(event.getEventType())) {
      // get the last 2 simulations
      Exercise lastSimulation =
          exerciseService.previousFinishedSimulation(event.getResourceId(), event.getTimestamp());
      if (lastSimulation == null || lastSimulation.getEnd().isEmpty()) {
        return;
      }
      Exercise secondLastSimulation =
          exerciseService.previousFinishedSimulation(
              event.getResourceId(), lastSimulation.getEnd().get());
      if (secondLastSimulation == null) {
        return;
      }

      // create map with the results to facilitate the computing of the score difference
      // TODO update exerciseService to return a map with result
      Map<ExpectationType, ExpectationResultsByType> lastSimulationResultsMap =
          exerciseService.getGlobalResults(lastSimulation.getId()).stream()
              .collect(Collectors.toMap(ExpectationResultsByType::type, Function.identity()));
      Map<ExpectationType, ExpectationResultsByType> secondLastSimulationResultsMap =
          exerciseService.getGlobalResults(secondLastSimulation.getId()).stream()
              .collect(Collectors.toMap(ExpectationResultsByType::type, Function.identity()));

      if (exerciseService.isThereAScoreDegradation(
          lastSimulationResultsMap, secondLastSimulationResultsMap)) {
        Scenario scenario = lastSimulation.getScenario();
        notificationEngineService.handleEventWithMessage(
            NotificationResourceCatalog.SCENARIO,
            scenario.getId(),
            scenario.getTenant() != null ? scenario.getTenant().getId() : null,
            NotificationTriggerEventType.SCORE_DEGRADATION,
            buildDegradationMessage(
                scenario,
                lastSimulation,
                secondLastSimulation,
                lastSimulationResultsMap,
                secondLastSimulationResultsMap));
      }
    }
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
