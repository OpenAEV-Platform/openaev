package io.openaev.notification.handler;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.*;
import io.openaev.expectation.ExpectationType;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.scenario.service.ScenarioStatisticService;
import io.openaev.service.NotificationRuleService;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScenarioNotificationEventHandler implements NotificationEventHandler {
  private final EntityManager entityManager;
  private final OpenAEVConfig openAEVConfig;
  private final ExerciseService exerciseService;
  private final NotificationRuleService notificationRuleService;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void handle(NotificationEvent event) {
    // Disable tenant filter — this handler runs cross-tenant
    log.info("start handling ScenarioNotificationEvent");
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    if (NotificationEventType.SIMULATION_COMPLETED.equals(event.getEventType())) {
      log.debug(
          "ScenarioNotificationEventHandler: SIMULATION_COMPLETED for scenarioId={}",
          event.getResourceId());
      // get the last 2 simulations
      Exercise lastSimulation =
          exerciseService.previousFinishedSimulation(event.getResourceId(), event.getTimestamp());
      if (lastSimulation == null || lastSimulation.getEnd().isEmpty()) {
        log.debug(
            "ScenarioNotificationEventHandler: no last finished simulation found for scenarioId={} — skipping",
            event.getResourceId());
        return;
      }
      Exercise secondLastSimulation =
          exerciseService.previousFinishedSimulation(
              event.getResourceId(), lastSimulation.getEnd().get());
      if (secondLastSimulation == null) {
        log.debug(
            "ScenarioNotificationEventHandler: no second-to-last simulation found for scenarioId={} — skipping (need at least 2 runs)",
            event.getResourceId());
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

      log.debug(
          "ScenarioNotificationEventHandler: last simulation={} results={}, second-last simulation={} results={}",
          lastSimulation.getId(),
          lastSimulationResultsMap,
          secondLastSimulation.getId(),
          secondLastSimulationResultsMap);

      if (exerciseService.isThereAScoreDegradation(
          lastSimulationResultsMap, secondLastSimulationResultsMap)) {

        log.debug(
            "ScenarioNotificationEventHandler: score degradation detected for scenarioId={} — activating notification rules",
            event.getResourceId());
        // notify
        notificationRuleService.activateNotificationRules(
            lastSimulation.getScenario().getId(),
            NotificationRuleTrigger.DIFFERENCE,
            buildScenarioNotificationData(
                lastSimulation.getScenario(),
                lastSimulation,
                secondLastSimulation,
                lastSimulationResultsMap,
                secondLastSimulationResultsMap));
      } else {
        log.debug(
            "ScenarioNotificationEventHandler: no score degradation for scenarioId={} — no notification sent",
            event.getResourceId());
      }
    }
  }

  private Map<String, String> buildScenarioNotificationData(
      @NotNull final Scenario scenario,
      @NotNull final Exercise lastSimulation,
      @NotNull final Exercise secondLastSimulation,
      @NotNull final Map<ExpectationType, ExpectationResultsByType> lastSimulationResultsMap,
      @NotNull
          final Map<ExpectationType, ExpectationResultsByType> secondLastSimulationResultsMap) {
    // TODO handle date format dynamically
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.systemDefault());

    String scenarioId = scenario.getId();
    String url = openAEVConfig.getBaseUrl();
    float lastSimulationPrevScore =
        getRoundedPercentageSafe(lastSimulationResultsMap.get(ExpectationType.PREVENTION));
    float lastSimulationDetectScore =
        getRoundedPercentageSafe(lastSimulationResultsMap.get(ExpectationType.DETECTION));
    float secondLastSimulationPrevScore =
        getRoundedPercentageSafe(secondLastSimulationResultsMap.get(ExpectationType.PREVENTION));
    float secondLastSimulationDetectScore =
        getRoundedPercentageSafe(secondLastSimulationResultsMap.get(ExpectationType.DETECTION));
    float decreasePrev = secondLastSimulationPrevScore - lastSimulationPrevScore;
    float decreaseDetect = secondLastSimulationDetectScore - lastSimulationDetectScore;

    Map<String, String> data = new HashMap<>();
    data.put("decrease_prev", Float.toString(decreasePrev));
    data.put("decrease_detect", Float.toString(decreaseDetect));
    data.put(
        "prev_simulation_date", secondLastSimulation.getEnd().map(formatter::format).orElse("NA"));
    data.put("prev_percentage_detection", Float.toString(secondLastSimulationDetectScore));
    data.put("prev_percentage_prevention", Float.toString(secondLastSimulationPrevScore));
    data.put("new_simulation_date", lastSimulation.getEnd().map(formatter::format).orElse("NA"));
    data.put("new_percentage_detection", Float.toString(lastSimulationDetectScore));
    data.put("new_percentage_prevention", Float.toString(lastSimulationPrevScore));
    data.put("scenarioLink", String.format("%s/admin/scenarios/%s", url, scenarioId));
    data.put("instanceLink", url);
    data.put("scenario_name", scenario.getName());
    return data;
  }

  /** Returns 0 if the expectation type has no results (null), avoiding NPE in getRoundedPercentage. */
  private static float getRoundedPercentageSafe(
      final ExpectationResultsByType expectationResultsByType) {
    if (expectationResultsByType == null) {
      return 0f;
    }
    return ScenarioStatisticService.getRoundedPercentage(expectationResultsByType);
  }
}
