package io.openaev.service.chaining;

import io.openaev.api.chaining.ActionStep;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.BadRequestException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class StepService {
  private final WorkflowService workflowService;
  private final StepRepository stepRepository;
  private final InjectExecutionStep injectExecutionStep;

  private final ConditionService conditionService;

  public Step saveStep(Step step) {
    return this.stepRepository.save(step);
  }

  public Step findStepTemplateById(String idStep) {
    return this.stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(
        idStep, STEP_STATUS.TEMPLATE);
  }

  public Step findStepWaitById(String idStep) {
    return this.stepRepository.findByStepTemplateIdIsNotNullAndIdAndStatus(
        idStep, STEP_STATUS.WAIT);
  }

  public void createSteps(String workflowId, List<StepsCreateInput.StepCreateInput> steps) {
    Workflow workflow = workflowService.getWorkflowById(workflowId);
    for (StepsCreateInput.StepCreateInput stepInput : steps) {
      ActionStep actionStep = this.factoryAction(stepInput.getStepAction());
      if (actionStep == null) throw new BadRequestException("action step is null");
      Step step = actionStep.create(stepInput, workflow);
      step = this.saveStep(step);
      this.stepCondition(stepInput, step, workflow);

      this.saveStep(step);
    }
  }

  public void run(Step step) {
    ActionStep actionStep = this.factoryAction(step.getStepAction());
    if (actionStep == null) throw new BadRequestException("action step is null");

    Step stepRun = actionStep.run(step, null);
    if (stepRun == null) {
      step.setStatus(STEP_STATUS.END);
      this.saveStep(step);

    } else {
      stepRun.setStatus(STEP_STATUS.RUN);
      this.saveStep(stepRun);
    }
  }

  public void wait(Step stepTemplate) {
    ActionStep actionStep = this.factoryAction(stepTemplate.getStepAction());
    if (actionStep == null) throw new BadRequestException("action step is null");

    Step stepWait = actionStep.wait(stepTemplate, null);

    stepWait = this.saveStep(stepWait);

    stepTemplate.getStepsExecuted().add(stepWait); // TODO Check
    this.saveStep(stepTemplate);
  }

  private ActionStep factoryAction(STEP_ACTION_CLASS actionClass) {
    return switch (actionClass) {
      case STEP_ACTION_CLASS.INJECT_EXECUTION -> injectExecutionStep;
      default -> null;
    };
  }

  public void saveStep(List<Step> steps) {
    this.stepRepository.saveAll(steps);
  }

  private Condition stepCondition(
      StepsCreateInput.StepCreateInput stepInput, Step step, Workflow workflow) {
    ConditionCreateInput firstCondition =
        stepInput.conditions.stream()
            .filter(
                conditionCreateInput ->
                    conditionCreateInput.getTemporaryIdConditionParent() == null)
            .reduce(
                (a, b) -> {
                  throw new IllegalArgumentException("Only 1 condition can be first parent");
                })
            .orElseThrow(
                () -> new IllegalArgumentException("Only 1 condition can be first parent"));

    Condition first =
        Condition.builder()
            .step(step)
            .type(firstCondition.getType())
            .key(firstCondition.getKey())
            .value(firstCondition.getValue())
            .build();
    first = conditionService.saveCondition(first);

    Map<String, Condition> temporaryIdAndSaveId = new HashMap<>();
    temporaryIdAndSaveId.put(firstCondition.getTemporaryId(), first);

    Map<String, List<ConditionCreateInput>> temporaryConditions = new HashMap<>();
    temporaryConditions =
        stepInput.getConditions().stream()
            .filter(
                conditionCreateInput ->
                    conditionCreateInput.getTemporaryIdConditionParent() != null)
            .collect(Collectors.groupingBy(ConditionCreateInput::getTemporaryIdConditionParent));

    Queue<String> currentId = new LinkedList<>();
    currentId.add(firstCondition.getTemporaryId());

    while (!currentId.isEmpty()) {
      String currentTemporaryId = currentId.poll();

      List<ConditionCreateInput> conditions =
          temporaryConditions.getOrDefault(currentTemporaryId, new ArrayList<>());

      for (ConditionCreateInput condition : conditions) {
        Condition current =
            Condition.builder()
                .type(condition.getType())
                .key(condition.getKey())
                .value(condition.getValue())
                .conditionParent(
                    temporaryIdAndSaveId.get(condition.getTemporaryIdConditionParent()))
                .step(step)
                .build();

        current = conditionService.saveCondition(current);

        temporaryIdAndSaveId.put(condition.getTemporaryId(), current);

        currentId.add(condition.getTemporaryId());
      }
    }
    return first;
  }

  public Step findById(String stepId) {
    return stepRepository.findById(stepId).orElseThrow();//todo exc
  }
}
