package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.scenario.response.ScenarioStatistic;
import io.openaev.rest.scenario.service.ScenarioStatisticService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ScenarioStatisticApi extends RestBehavior {

  private final ScenarioStatisticService scenarioStatisticService;

  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/statistics",
    TENANT_SCENARIO_URI + "/{scenarioId}/statistics"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Retrieve scenario statistics")
  public ScenarioStatistic getScenarioStatistics(
      TxCtx ctx, @PathVariable @NotBlank final String scenarioId) {
    return scenarioStatisticService.getStatistics(scenarioId);
  }
}
