package io.openaev.api.chaining;

import static io.openaev.api.chaining.ChainingApi.CHAINING_URI;
import static io.openaev.api.chaining.ChainingApi.TENANT_CHAINING_URI;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.aop.AccessControl;
import io.openaev.api.chaining.dto.ChainingOutput;
import io.openaev.api.chaining.dto.EventOutput;
import io.openaev.api.chaining.dto.StepOutput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.TagRepository;
import io.openaev.helper.StreamHelper;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exercise.form.CreateExerciseInput;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.scenario.form.ScenarioInput;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.chaining.ConditionService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping({CHAINING_URI, TENANT_CHAINING_URI})
public class ChainingApi extends RestBehavior {

  public static final String TENANT_CHAINING_URI = TENANT_PREFIX + "/chaining";
  public static final String CHAINING_URI = "/api/chaining";
  public static final String SIMULATION_URI = "/simulations";
  public static final String SCENARIO_URI = "/scenarios";

  private final ExerciseService exerciseService;
  private final CustomDashboardService customDashboardService;
  private final PlatformSettingsService platformSettingsService;
  private final ScenarioService scenarioService;
  private final WorkflowService workflowService;
  private final StepService stepService;
  private final TagRepository tagRepository;
  private final ConditionService conditionService;

  // -- READ --

  @Operation(summary = "Get all chaining data", description = "Returns all conditions and steps")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Chaining data retrieved successfully")
  })
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @GetMapping
  public ChainingOutput findAll() {
    List<EventOutput> conditions =
        conditionService.findAll().stream().map(ConditionMapper::toOutput).toList();

    List<StepOutput> steps =
        stepService.findAllStepTemplates().stream().map(StepMapper::toOutput).toList();

    return new ChainingOutput(conditions, steps);
  }

  // CREATE SIMULATION
  @PostMapping(SIMULATION_URI)
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.SIMULATION)
  public Exercise createSimulation(@Valid @RequestBody CreateExerciseInput input)
      throws ChainingException {

    workflowService.isPreviewFeatureChainingEnable();

    if (input == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simulation input cannot be null");

    Exercise simulation = new Exercise();
    simulation.setUpdateAttributes(input);
    simulation.setTags(
        StreamHelper.iterableToSet(this.tagRepository.findAllById(input.getTagIds())));

    if (hasText(input.getCustomDashboard())) {
      simulation.setCustomDashboard(
          this.customDashboardService.customDashboard(input.getCustomDashboard()));
    } else {
      simulation.setCustomDashboard(
          this.platformSettingsService
              .setting(SettingKeys.DEFAULT_SIMULATION_DASHBOARD.key())
              .map(Setting::getValue)
              .filter(v -> !v.isEmpty())
              .map(this.customDashboardService::customDashboard)
              .orElse(null));
    }
    return this.exerciseService.createSimulationChaining(simulation);
  }

  @PostMapping(SIMULATION_URI + "/{simulationId}/injects")
  @AccessControl(
      resourceId = "#simulationId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public void createInjectForSimulationChaining(
      @PathVariable String simulationId, @Valid @RequestBody InjectInput input)
      throws ChainingException {

    workflowService.isPreviewFeatureChainingEnable();

    if (workflowService.isSimulationChaining(simulationId)) {
      exerciseService.findById(simulationId);

      StepsCreateInput.StepInput step = InjectExecutionStep.getInjectAsStepsCreateInput(input);

      Workflow workflow =
          workflowService
              .findWorkflowTemplateBySimulationId(simulationId)
              .orElseThrow(
                  () ->
                      new ChainingException(
                          "Simulation is configured for chaining but no workflow TEMPLATE found. Simulation ID: "
                              + simulationId));

      stepService.createStepTemplates(workflow.getId(), List.of(step));

      // Todo return Action, Event and Link
    }
  }

  @PostMapping(SIMULATION_URI + "/{simulationId}")
  @AccessControl(
      resourceId = "#simulationId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise duplicateExercise(@PathVariable @NotBlank final String simulationId)
      throws ChainingException {
    workflowService.isPreviewFeatureChainingEnable();

    Exercise simulation = exerciseService.getDuplicateExercise(simulationId);
    Optional<Workflow> workflowOpt =
        workflowService.findWorkflowTemplateBySimulationId(simulationId);
    if (workflowOpt.isEmpty())
      throw new ChainingException("No workflow TEMPLATE found. Simulation ID: " + simulationId);

    Workflow workflowFrom = workflowOpt.get();
    Workflow workflowTo = workflowService.duplicateSimulation(simulationId, simulation);
    stepService.copyStepTemplate(workflowFrom, workflowTo);

    return simulation;
  }

  // CREATE SCENARIO
  @PostMapping(SCENARIO_URI)
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.SCENARIO)
  public Scenario createScenarioChaining(@Valid @RequestBody final ScenarioInput input)
      throws ChainingException {

    workflowService.isPreviewFeatureChainingEnable();

    if (input == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scenario input cannot be null");

    Scenario scenario = new Scenario();
    scenario.setUpdateAttributes(input);
    scenario.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    if (hasText(input.getCustomDashboard())) {
      scenario.setCustomDashboard(
          this.customDashboardService.customDashboard(input.getCustomDashboard()));
    } else {
      scenario.setCustomDashboard(
          this.platformSettingsService
              .setting(SettingKeys.DEFAULT_SCENARIO_DASHBOARD.key())
              .map(Setting::getValue)
              .filter(v -> !v.isEmpty())
              .map(this.customDashboardService::customDashboard)
              .orElse(null));
    }
    return this.scenarioService.createScenarioChaining(scenario);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/injects")
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public void createInjectForScenarioChaining(
      @PathVariable @NotBlank final String scenarioId, @Valid @RequestBody InjectInput input)
      throws ChainingException {
    workflowService.isPreviewFeatureChainingEnable();

    if (workflowService.isScenarioChaining(scenarioId)) {
      this.scenarioService.scenario(scenarioId);

      StepsCreateInput.StepInput step = InjectExecutionStep.getInjectAsStepsCreateInput(input);

      Workflow workflow =
          workflowService
              .findWorkflowTemplateByScenarioId(scenarioId)
              .orElseThrow(
                  () ->
                      new ChainingException(
                          "Scenario is configured for chaining but no workflow TEMPLATE found. Scenario ID: "
                              + scenarioId));

      stepService.createStepTemplates(workflow.getId(), List.of(step));
      // Todo return Action, Event and Link
    }
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}")
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.SCENARIO)
  public Scenario duplicateScenarioChaining(@PathVariable @NotBlank final String scenarioId)
      throws ChainingException {

    workflowService.isPreviewFeatureChainingEnable();

    Scenario scenario = scenarioService.getDuplicateScenario(scenarioId);
    Optional<Workflow> workflowOpt = workflowService.findWorkflowTemplateByScenarioId(scenarioId);
    if (workflowOpt.isEmpty())
      throw new ChainingException("No workflow TEMPLATE found. Scenario ID: " + scenarioId);

    Workflow workflowFrom = workflowOpt.get();
    Workflow workflowTo = workflowService.duplicateScenario(scenarioId, scenario);
    stepService.copyStepTemplate(workflowFrom, workflowTo);

    return scenario;
  }
}
