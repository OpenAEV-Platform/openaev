package io.openaev.api.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.api.catalog.CatalogFeature;
import io.openaev.api.catalog.dto.scenario.ScenarioTargetsInput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exercise.ExerciseResponseHydrator;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;
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
  private final ExerciseService exerciseService;
  private final CatalogFeature catalogFeature;

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/exercise/running",
    TENANT_SCENARIO_URI + "/{scenarioId}/exercise/running"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SCENARIO)
  @Operation(summary = "Start a simulation, with targets for it only")
  public Exercise launch(
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody(required = false) final ScenarioTargetsInput targets)
      throws ChainingException {
    TargetChoice choice = targets == null ? TargetChoice.NONE : targets.toChoice();
    if (!choice.isEmpty()) {
      catalogFeature.require();
    }
    String simulationId = scenarioLaunchService.launch(ctx, scenarioId, choice);
    return ExerciseResponseHydrator.hydrateForResponse(exerciseService.exercise(ctx, simulationId));
  }
}
