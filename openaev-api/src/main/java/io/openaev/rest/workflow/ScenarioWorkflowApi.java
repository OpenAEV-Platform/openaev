package io.openaev.rest.workflow;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.workflow.form.StepInput;
import io.openaev.rest.workflow.form.ConditionInput;
import io.openaev.rest.workflow.form.WorkflowOutputDto;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ScenarioWorkflowApi extends RestBehavior {

  private final WorkflowService workflowService;
  private final ScenarioService scenarioService;
  private final WorkflowRepository workflowRepository;
  private final StepRepository stepRepository;
  private final ConditionRepository conditionRepository;

  // -- WORKFLOW --

  @GetMapping(SCENARIO_URI + "/{scenarioId}/workflow")
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public WorkflowOutputDto getOrCreateWorkflow(
      @PathVariable @NotBlank final String scenarioId) {
    Scenario scenario = scenarioService.scenario(scenarioId);
    Workflow workflow =
        workflowRepository
            .findByScenario_IdAndStatus(scenarioId, WorkflowStatus.TEMPLATE)
            .orElseGet(
                () -> {
                  Workflow newWorkflow =
                      Workflow.builder()
                          .version(0)
                          .status(WorkflowStatus.TEMPLATE)
                          .scenario(scenario)
                          .build();
                  return workflowRepository.save(newWorkflow);
                });
    List<Step> steps = stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId());
    List<Condition> conditions =
        steps.stream()
            .flatMap(step -> conditionRepository.findAllByStep_Id(step.getId()).stream())
            .toList();
    return WorkflowOutputDto.from(workflow, steps, conditions);
  }

  // -- STEPS --

  @PostMapping(SCENARIO_URI + "/{scenarioId}/workflow/steps")
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public WorkflowOutputDto createStep(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final StepInput input) {
    Workflow workflow = getWorkflowForScenario(scenarioId);
    Step step =
        Step.builder()
            .stepAction(input.getStepAction())
            .limitExecution(input.getLimitExecution())
            .data(input.getData())
            .output_parser(input.getOutputParser())
            .fieldScope(input.getFieldScope() != null ? input.getFieldScope() : StepFieldScope.GLOBAL)
            .status(StepStatus.TEMPLATE)
            .workflow(workflow)
            .build();
    stepRepository.save(step);
    workflow.setEdited(true);
    workflowRepository.save(workflow);
    return buildWorkflowOutput(workflow);
  }

  @PutMapping(SCENARIO_URI + "/{scenarioId}/workflow/steps/{stepId}")
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public WorkflowOutputDto updateStep(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String stepId,
      @Valid @RequestBody final StepInput input) {
    Workflow workflow = getWorkflowForScenario(scenarioId);
    Step step =
        stepRepository
            .findById(stepId)
            .orElseThrow(() -> new ElementNotFoundException("Step not found: " + stepId));
    step.setStepAction(input.getStepAction());
    step.setLimitExecution(input.getLimitExecution());
    step.setData(input.getData());
    step.setOutput_parser(input.getOutputParser());
    if (input.getFieldScope() != null) {
      step.setFieldScope(input.getFieldScope());
    }
    stepRepository.save(step);
    workflow.setEdited(true);
    workflowRepository.save(workflow);
    return buildWorkflowOutput(workflow);
  }

  @DeleteMapping(SCENARIO_URI + "/{scenarioId}/workflow/steps/{stepId}")
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public WorkflowOutputDto deleteStep(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String stepId) {
    Workflow workflow = getWorkflowForScenario(scenarioId);
    stepRepository.deleteById(stepId);
    workflow.setEdited(true);
    workflowRepository.save(workflow);
    return buildWorkflowOutput(workflow);
  }

  // -- CONDITIONS --

  @PostMapping(SCENARIO_URI + "/{scenarioId}/workflow/steps/{stepId}/conditions")
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public WorkflowOutputDto createCondition(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String stepId,
      @Valid @RequestBody final ConditionInput input) {
    Workflow workflow = getWorkflowForScenario(scenarioId);
    Step step =
        stepRepository
            .findById(stepId)
            .orElseThrow(() -> new ElementNotFoundException("Step not found: " + stepId));
    Step stepFrom =
        input.getStepFromId() != null
            ? stepRepository
                .findById(input.getStepFromId())
                .orElseThrow(
                    () ->
                        new ElementNotFoundException(
                            "StepFrom not found: " + input.getStepFromId()))
            : null;
    Condition parentCondition =
        input.getConditionParentId() != null
            ? conditionRepository
                .findById(input.getConditionParentId())
                .orElseThrow(
                    () ->
                        new ElementNotFoundException(
                            "Parent condition not found: " + input.getConditionParentId()))
            : null;
    Condition condition =
        Condition.builder()
            .step(step)
            .stepFrom(stepFrom)
            .key(input.getKey())
            .value(input.getValue())
            .type(input.getType())
            .conditionParent(parentCondition)
            .build();
    conditionRepository.save(condition);
    workflow.setEdited(true);
    workflowRepository.save(workflow);
    return buildWorkflowOutput(workflow);
  }

  @PutMapping(SCENARIO_URI + "/{scenarioId}/workflow/conditions/{conditionId}")
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public WorkflowOutputDto updateCondition(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String conditionId,
      @Valid @RequestBody final ConditionInput input) {
    Workflow workflow = getWorkflowForScenario(scenarioId);
    Condition condition =
        conditionRepository
            .findById(conditionId)
            .orElseThrow(
                () -> new ElementNotFoundException("Condition not found: " + conditionId));
    condition.setKey(input.getKey());
    condition.setValue(input.getValue());
    condition.setType(input.getType());
    if (input.getStepFromId() != null) {
      Step stepFrom =
          stepRepository
              .findById(input.getStepFromId())
              .orElseThrow(
                  () ->
                      new ElementNotFoundException(
                          "StepFrom not found: " + input.getStepFromId()));
      condition.setStepFrom(stepFrom);
    }
    conditionRepository.save(condition);
    workflow.setEdited(true);
    workflowRepository.save(workflow);
    return buildWorkflowOutput(workflow);
  }

  @DeleteMapping(SCENARIO_URI + "/{scenarioId}/workflow/conditions/{conditionId}")
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public WorkflowOutputDto deleteCondition(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String conditionId) {
    Workflow workflow = getWorkflowForScenario(scenarioId);
    conditionRepository.deleteById(conditionId);
    workflow.setEdited(true);
    workflowRepository.save(workflow);
    return buildWorkflowOutput(workflow);
  }

  // -- HELPERS --

  private Workflow getWorkflowForScenario(String scenarioId) {
    return workflowRepository
        .findByScenario_IdAndStatus(scenarioId, WorkflowStatus.TEMPLATE)
        .orElseThrow(
            () ->
                new ElementNotFoundException(
                    "Workflow not found for scenario: " + scenarioId));
  }

  private WorkflowOutputDto buildWorkflowOutput(Workflow workflow) {
    List<Step> steps = stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId());
    List<Condition> conditions =
        steps.stream()
            .flatMap(step -> conditionRepository.findAllByStep_Id(step.getId()).stream())
            .toList();
    return WorkflowOutputDto.from(workflow, steps, conditions);
  }
}
