package io.openaev.rest.variable;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.variable.form.VariableInput;
import io.openaev.service.VariableService;
import io.openaev.service.scenario.ScenarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class VariableApi extends RestBehavior {

  private final VariableService variableService;
  private final ScenarioService scenarioService;
  private final ExerciseRepository exerciseRepository;

  // -- EXERCISES --

  @PostMapping({
    EXERCISE_URI + "/{exerciseId}/variables",
    TENANT_EXERCISE_URI + "/{exerciseId}/variables"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Variable createVariableForExercise(
      TxCtx ctx,
      @PathVariable @NotBlank final String exerciseId,
      @Valid @RequestBody final VariableInput input) {
    Variable variable = new Variable();
    variable.setUpdateAttributes(input);
    Exercise exercise =
        this.exerciseRepository
            .findByIdAndTenantId(exerciseId, TenantContext.getCurrentTenant())
            .orElseThrow(ElementNotFoundException::new);
    variable.setExercise(exercise);
    return this.variableService.createVariable(variable);
  }

  @GetMapping({
    EXERCISE_URI + "/{exerciseId}/variables",
    TENANT_EXERCISE_URI + "/{exerciseId}/variables"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Variable> variablesFromExercise(
      TxCtx ctx, @PathVariable @NotBlank final String exerciseId) {
    return this.variableService.variablesFromExercise(exerciseId);
  }

  @PutMapping({
    EXERCISE_URI + "/{exerciseId}/variables/{variableId}",
    TENANT_EXERCISE_URI + "/{exerciseId}/variables/{variableId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Variable updateVariableForExercise(
      TxCtx ctx,
      @PathVariable @NotBlank final String exerciseId,
      @PathVariable @NotBlank final String variableId,
      @Valid @RequestBody final VariableInput input) {
    Variable variable = this.variableService.variableForExercise(variableId, exerciseId);
    variable.setUpdateAttributes(input);
    return this.variableService.updateVariable(variable);
  }

  @DeleteMapping({
    EXERCISE_URI + "/{exerciseId}/variables/{variableId}",
    TENANT_EXERCISE_URI + "/{exerciseId}/variables/{variableId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public void deleteVariableForExercise(
      TxCtx ctx,
      @PathVariable @NotBlank final String exerciseId,
      @PathVariable @NotBlank final String variableId) {
    this.variableService.variableForExercise(variableId, exerciseId);
    this.variableService.deleteVariable(variableId);
  }

  // -- SCENARIOS --

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/variables",
    TENANT_SCENARIO_URI + "/{scenarioId}/variables"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Variable createVariableForScenario(
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final VariableInput input) {
    Variable variable = new Variable();
    variable.setUpdateAttributes(input);
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    variable.setScenario(scenario);
    return this.variableService.createVariable(variable);
  }

  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/variables",
    TENANT_SCENARIO_URI + "/{scenarioId}/variables"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<Variable> variablesFromScenario(
      TxCtx ctx, @PathVariable @NotBlank final String scenarioId) {
    return this.variableService.variablesFromScenario(scenarioId);
  }

  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/variables/{variableId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/variables/{variableId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Variable updateVariableForScenario(
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String variableId,
      @Valid @RequestBody final VariableInput input) {
    Variable variable = this.variableService.variableForScenario(variableId, scenarioId);
    variable.setUpdateAttributes(input);
    return this.variableService.updateVariable(variable);
  }

  @DeleteMapping({
    SCENARIO_URI + "/{scenarioId}/variables/{variableId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/variables/{variableId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void deleteVariableForScenario(
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String variableId) {
    this.variableService.variableForScenario(variableId, scenarioId);
    this.variableService.deleteVariable(variableId);
  }
}
