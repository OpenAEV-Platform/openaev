package io.openaev.service.chaining;

import io.openaev.database.model.*;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.rest.exception.ChainingException;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class ConditionService {
  private final ConditionRepository conditionRepository;
  private final QueueChainingService queueChainingService;

  /**
   * Checks whether the condition is a time-based condition.
   *
   * @param condition condition to evaluate
   * @return {@code true} if the condition type is AFTER or BEFORE, {@code false} otherwise
   */
  public boolean isTimeCondition(Condition condition) {
    return switch (condition.getType()) {
      case ConditionType.AFTER, ConditionType.BEFORE -> true;
      default -> false;
    };
  }

  /**
   * Checks whether the condition is a mapper condition.
   *
   * @param condition condition to evaluate
   * @return {@code true} if the condition type is MAPPER, {@code false} otherwise
   */
  public boolean isMapperCondition(Condition condition) {
    return switch (condition.getType()) {
      case ConditionType.MAPPER -> true;
      default -> false;
    };
  }

  public Condition isMapperConditionValid(Condition condition, String input, String data) {
    // todo
    // return conditionExecution
    return null;
  }

  /**
   * Checks whether the condition is a filter condition.
   *
   * @param condition condition to evaluate
   * @return {@code true} if the condition is a filter condition, {@code false} otherwise
   */
  public boolean isFilterCondition(Condition condition) {
    return switch (condition.getType()) {
      case ConditionType.AFTER, ConditionType.BEFORE, ConditionType.MAPPER -> false;
      default -> true;
    };
  }

  public Condition isFilterConditionValid(Condition condition, String input, String data) {
    // todo
    // return conditionExecution
    return null;
  }

  /**
   * Evaluates a time condition against the current time.
   *
   * @param conditionTemplate condition template to evaluate
   * @return a validated condition if applicable, or {@code null} otherwise
   */
  // TODO: this is for legacy behavior only (compare from start of workflow instead of previous
  // step)
  public Boolean isTimeConditionValid(Condition conditionTemplate, Instant now, Instant goal) {
    if (conditionTemplate.getType().equals(ConditionType.AFTER)) {
      return now.isAfter(goal);
    } else if (conditionTemplate.getType().equals(ConditionType.BEFORE)) {
      // Case BEFORE: an output should append before a date;
      return now.isBefore(goal);
    }
    return false;
  }

  /**
   * Saves a condition.
   *
   * @param condition condition to persist
   * @return the saved condition
   */
  public Condition saveCondition(Condition condition) {
    return conditionRepository.save(condition);
  }

  /**
   * Saves multiple conditions.
   *
   * @param conditions conditions to persist
   * @return the saved conditions
   */
  public List<Condition> saveAllConditions(List<Condition> conditions) {
    return conditionRepository.saveAll(conditions);
  }

  /**
   * Retrieves all conditions associated with a step.
   *
   * @param stepId step identifier
   * @return list of conditions linked to the step
   */
  public List<Condition> findAllConditionsByStepId(String stepId) {
    return conditionRepository.findAllByStep_Id(stepId);
  }

  /**
   * Evaluates the conditions associated with a given step and determines if required conditions are
   * valid for execution.
   *
   * <p>This method retrieves all conditions for the specified step, filters time-based conditions,
   * and checks if each condition is valid based on the workflow start time and the current time. If
   * a time-based "AFTER" condition is not yet valid, it schedules the step to be executed after the
   * delay.
   *
   * @param nextStepTemplateToExecute the step template to be evaluated
   * @param input the input data for the step execution
   * @param workflowRun the workflow instance in which the step is being executed
   * @param stepService the service used to interact with steps and related logic
   * @return a list of conditions that are valid and ready for execution, or {@code null} if a
   *     delayed "AFTER" condition has been scheduled
   */
  public List<Condition> checkCondition(
      Step nextStepTemplateToExecute, String input, Workflow workflowRun, StepService stepService)
      throws ChainingException {
    List<Condition> conditionTemplate =
        findAllConditionsByStepId(nextStepTemplateToExecute.getId());
    // No condition means direct execution:
    if (conditionTemplate == null || conditionTemplate.isEmpty()) return new ArrayList<>();

    List<Condition> conditionsExecution = new ArrayList<>();
    List<Condition> timeConditions =
        conditionTemplate.stream().filter(this::isTimeCondition).toList();

    // TODO manage multi time condition (AND, OR: g C1 BEFORE OR C2 AFTER)
    for (Condition condition : timeConditions) {
      // Compute expected start time for the condition to be considered as valid
      Instant now = Instant.now();
      Instant start = workflowRun.getWorkflowCreatedAt();
      // TODO: can this happen ? Shouldn't it throw an exception instead?
      if (start == null) start = now;

      long value = Long.parseLong(condition.getValue());
      Instant goal = start.plus(value, ChronoUnit.MILLIS);

      boolean timeConditionValid = isTimeConditionValid(condition, now, goal);

      if (timeConditionValid) {
        Condition conditionExecution =
            Condition.builder()
                .key(now.toString())
                .type(condition.getType())
                .value(goal.toString())
                .build();
        conditionsExecution.add(conditionExecution);
        continue;
      }

      if (condition.getType().equals(ConditionType.AFTER)) {
        long delay = ChronoUnit.MILLIS.between(now, goal);
        try {
          queueChainingService.delayStep(nextStepTemplateToExecute, workflowRun, delay);
        } catch (IOException e) {
          throw new ChainingException(
              "Failed to push step (TEMPLATE) into delay queue. Step ID: "
                  + nextStepTemplateToExecute.getId(),
              e);
        }
        return null;
      }
    }

    // Filter conditions
    List<Condition> filterConditions =
        conditionTemplate.stream().filter(this::isFilterCondition).toList();

    for (Condition condition : filterConditions) {
      Condition filterConditionValid =
          isFilterConditionValid(condition, input, nextStepTemplateToExecute.getData());
      if (filterConditionValid == null) {
        // todo condition not valid break analyse
      } else {
        conditionsExecution.add(filterConditionValid);
      }
    }

    // Mapper conditions
    List<Condition> mapperConditions =
        conditionTemplate.stream().filter(this::isMapperCondition).toList();

    for (Condition condition : mapperConditions) {
      Condition mapperConditionValid =
          isMapperConditionValid(condition, input, nextStepTemplateToExecute.getData());
      if (mapperConditionValid == null) {
        // todo condition not valid break analyse
      } else {
        conditionsExecution.add(mapperConditionValid);
      }
    }

    List<Condition> stepFrom =
        conditionTemplate.stream().filter(condition -> condition.getStepFrom() != null).toList();
    for (Condition condition : stepFrom) {
      String idStepFromTemplate = condition.getStepFrom().getId();
      // List of step template depend on, that has been run
      List<Step> dependOnStepsRunByTemplateIdAndWorkflowRunId =
          stepService
              .findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
                  idStepFromTemplate, workflowRun.getId())
              .stream()
              .filter(step -> step.getOutput() != null)
              .toList();
      // Count of current step template already run into this workflow run
      int stepExecutedCount =
          stepService.countExecutedStep(workflowRun.getId(), nextStepTemplateToExecute.getId());

      boolean hasDependencyOutput = !dependOnStepsRunByTemplateIdAndWorkflowRunId.isEmpty();
      boolean underExecutionLimit =
          stepExecutedCount < nextStepTemplateToExecute.getLimitExecution();

      // todo : change : !dependOnStepsRunByTemplateIdAndWorkflowRunId.isEmpty()
      // ( means at least 1 stepFrom is/has been running),
      // to implement: check if input/output as already be used into the next stepToExecute
      // This condition means:
      // - the previews one has been executed and contain output
      // - and the next one not reach his limit of execution
      if (hasDependencyOutput && underExecutionLimit) {
        conditionsExecution.add(isDependOn(condition.getStepFrom().getId()));
      } else {
        // Todo : condition not valid break analyse
        return null;
      }
    }

    // todo Mapped input-data step
    return conditionsExecution;
  }

  /**
   * Creates a DEPEND_ON condition for a step template dependency.
   *
   * @param idStepFromTemplate identifier of the dependent step template
   * @return the created DEPEND_ON condition
   */
  public Condition isDependOn(String idStepFromTemplate) {
    return Condition.builder()
        .key("step_template_id")
        .type(ConditionType.DEPEND_ON)
        .value(idStepFromTemplate)
        .build();
  }
}
