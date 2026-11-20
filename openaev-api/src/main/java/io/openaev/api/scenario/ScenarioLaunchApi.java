package io.openaev.api.scenario;

import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.api.catalog.CatalogFeature;
import io.openaev.api.scenario.dto.ScenarioLaunchCheckOutput;
import io.openaev.api.scenario.dto.ScenarioLaunchOutput;
import io.openaev.api.scenario.dto.ScenarioTargetsInput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.ChainingException;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;
import io.openaev.service.scenario.ScenarioLaunchCheckService;
import io.openaev.service.scenario.ScenarioLaunchService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Checks what a scenario launch meets, and launches it with targets for this simulation only. */
@RestController
@RequiredArgsConstructor
public class ScenarioLaunchApi {

  private final ScenarioLaunchService scenarioLaunchService;
  private final ScenarioLaunchCheckService scenarioLaunchCheckService;
  private final CatalogFeature catalogFeature;

  // -- READ --

  @Operation(summary = "What blocks a scenario launch, or makes its injects fail")
  @GetMapping(TENANT_SCENARIO_URI + "/{scenarioId}/launch-check")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ScenarioLaunchCheckOutput launchCheck(TxCtx ctx, @PathVariable String scenarioId) {
    catalogFeature.require();
    return ScenarioLaunchCheckMapper.toOutput(scenarioLaunchCheckService.check(ctx, scenarioId));
  }

  // -- CREATE --

  @Operation(summary = "Start a simulation from the scenario, the chosen targets added to it only")
  @PostMapping(TENANT_SCENARIO_URI + "/{scenarioId}/launch")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SCENARIO)
  public ScenarioLaunchOutput launch(
      TxCtx ctx, @PathVariable String scenarioId, @Valid @RequestBody ScenarioTargetsInput input)
      throws ChainingException {
    catalogFeature.require();
    return new ScenarioLaunchOutput(
        scenarioLaunchService.launch(
            ctx,
            scenarioId,
            TargetChoice.of(
                input.assetIds(), input.assetGroupIds(), input.teamIds(), input.manualTargets())));
  }
}
