package io.openaev.api.scenario;

import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.api.scenario.dto.ScenarioLaunchOutput;
import io.openaev.api.scenario.dto.ScenarioTargetsInput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.ChainingException;
import io.openaev.service.scenario.ScenarioLaunchService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ScenarioLaunchApi {

  private final ScenarioLaunchService scenarioLaunchService;

  @PostMapping(TENANT_SCENARIO_URI + "/{scenarioId}/exercise/running")
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SCENARIO)
  @Operation(summary = "Start a simulation, with targets for it only")
  public ScenarioLaunchOutput launch(
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody(required = false) final ScenarioTargetsInput targets)
      throws ChainingException {
    return new ScenarioLaunchOutput(
        scenarioLaunchService.launch(ctx, scenarioId, ScenarioTargetsInput.choiceOf(targets)));
  }
}
