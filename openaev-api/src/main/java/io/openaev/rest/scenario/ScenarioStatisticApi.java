package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.scenario.response.ScenarioStatistic;
import io.openaev.rest.scenario.service.ScenarioStatisticService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ScenarioStatisticApi extends RestBehavior {

  private final ScenarioStatisticService scenarioStatisticService;

  @GetMapping(SCENARIO_URI + "/{scenarioId}/statistics")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Retrieve scenario statistics")
  public ScenarioStatistic getScenarioStatistics(@PathVariable @NotBlank final String scenarioId) {
    return scenarioStatisticService.getStatistics(scenarioId);
  }
}
