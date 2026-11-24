package io.openaev.api.scenario;

import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.scenario.dto.ScenarioLaunchCheckOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.scenario.ScenarioLaunchCheckService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequirePreviewFeature(PreviewFeature.CATALOG)
@RequiredArgsConstructor
public class ScenarioLaunchCheckApi {

  private final ScenarioLaunchCheckService scenarioLaunchCheckService;

  @Operation(summary = "What blocks a scenario launch, or makes its injects fail")
  @GetMapping(TENANT_SCENARIO_URI + "/{scenarioId}/launch-check")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ScenarioLaunchCheckOutput launchCheck(TxCtx ctx, @PathVariable String scenarioId) {
    return ScenarioLaunchCheckMapper.toOutput(scenarioLaunchCheckService.check(ctx, scenarioId));
  }
}
