package io.openaev.service.chaining;

import io.openaev.database.model.CONDITION_TYPE;
import io.openaev.database.model.Condition;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ConditionRepository;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ConditionService {
  private final ConditionRepository conditionRepository;
  private final QueueChainingService queueChainingService;

  public boolean isTimeCondition(Condition condition) {
    return switch (condition.getType()) {
      case CONDITION_TYPE.AFTER, CONDITION_TYPE.BEFORE -> true;
      default -> false;
    };
  }

  public boolean isMapperCondition(Condition condition) {
    return switch (condition.getType()) {
      case CONDITION_TYPE.MAPPER -> true;
      default -> false;
    };
  }

  public Condition isMapperConditionValid(Condition condition, String input, String data) {
    // todo
    // return conditionExecution
    return null;
  }

  public boolean isFilterCondition(Condition condition) {
    return switch (condition.getType()) {
      case CONDITION_TYPE.AFTER, CONDITION_TYPE.BEFORE, CONDITION_TYPE.MAPPER -> false;
      default -> true;
    };
  }

  public Condition isFilterConditionValid(Condition condition, String input, String data) {
    // todo
    // return conditionExecution
    return null;
  }

  // TODO: this is for legacy behavior only (compare from start of workflow instead of previous
  // step)
  public Condition isTimeConditionValid(
      Condition conditionTemplate, Workflow workflowRun, Instant now, Instant goal) {
    if (conditionTemplate.getType().equals(CONDITION_TYPE.AFTER)) {
      if (now.isAfter(goal)) {
        return Condition.builder()
            .key(now.toString())
            .type(conditionTemplate.getType())
            .value(goal.toString())
            .build();
      }
    } else if (conditionTemplate.getType().equals(CONDITION_TYPE.BEFORE)) {
      // todo check witch case with before?
      return Condition.builder()
          .key(now.toString())
          .type(conditionTemplate.getType())
          .value(goal.toString())
          .build();
    }
    return null;
  }

  public Condition saveCondition(Condition condition) {
    return conditionRepository.save(condition);
  }

  public List<Condition> saveAllConditions(List<Condition> conditions) {
    return conditionRepository.saveAll(conditions);
  }

  public List<Condition> findAllByStepId(String stepId) {
    return conditionRepository.findAllByStep_Id(stepId);
  }

  public List<Condition> checkCondition(
      Step nextStepTemplateToExecute,
      String input,
      String data,
      Workflow workflowRun,
      StepService stepService) {
    List<Condition> conditionTemplate = findAllByStepId(nextStepTemplateToExecute.getId());
    List<Condition> conditionExecution = new ArrayList<>();
    // No condition means direct execution:
    if (conditionTemplate == null || conditionTemplate.isEmpty()) return new ArrayList<>();
    // todo check Condition
    // todo First test time condition
    List<Condition> timeConditions =
        conditionTemplate.stream().filter(this::isTimeCondition).toList();

    for (Condition condition : timeConditions) {
      // Compute expected start time for the condition to be considered as valid
      Instant now = Instant.now();
      Instant start = workflowRun.getWorkflowCreatedAt();
      // TODO: can this happen ? Shouldn't it throw anexception instead?
      if (start == null) {
        start = now;
      }
      long value = Long.parseLong(condition.getValue());
      Instant goal = start.plus(value, ChronoUnit.MILLIS);

      Condition timeConditionValid = isTimeConditionValid(condition, workflowRun, now, goal);
      if (timeConditionValid == null) {
        long delay = ChronoUnit.MILLIS.between(now, goal);
        try {
          queueChainingService.delayStep(nextStepTemplateToExecute, workflowRun, delay);
        } catch (IOException e) {
          // TODO: better exception management
          throw new RuntimeException(e);
        }
        return null;
      } else {
        conditionExecution.add(timeConditionValid);
      }
    }

    List<Condition> filterConditions =
        conditionTemplate.stream().filter(this::isFilterCondition).toList();

    for (Condition condition : filterConditions) {
      Condition filterConditionValid = isFilterConditionValid(condition, input, data);
      if (filterConditionValid == null) {
        // todo condition not valid break analyse
      } else {
        conditionExecution.add(filterConditionValid);
      }
    }
    List<Condition> mapperConditions =
        conditionTemplate.stream().filter(this::isMapperCondition).toList();

    for (Condition condition : mapperConditions) {
      Condition mapperConditionValid = isMapperConditionValid(condition, input, data);
      if (mapperConditionValid == null) {
        // todo condition not valid break analyse
      } else {
        conditionExecution.add(mapperConditionValid);
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
      // Count of current step template already run (status != END) into this workflow run
      int stepExecutedCount =
          stepService.countExecutedStep(workflowRun.getId(), nextStepTemplateToExecute.getId());
      // todo : change : !dependOnStepsRunByTemplateIdAndWorkflowRunId.isEmpty()
      // ( means at least 1 stepFrom is/has been running),
      // to implement: check if input/output as already be used into the next stepToExecute
      // This condition means:
      // - the previews one has been executed and contain output
      // - and the next one not reach his limit of execution
      if (!dependOnStepsRunByTemplateIdAndWorkflowRunId.isEmpty()
          && stepExecutedCount < nextStepTemplateToExecute.getLimitExecution()) {
        conditionExecution.add(isDependOn(condition.getStepFrom().getId()));
      } else {
        // todo condition not valid break analyse
        return null;
      }
    }

    // todo Mapped input-data step
    return conditionExecution;
  }

  public Condition isDependOn(String idStepFromTemplate) {
    return Condition.builder()
        .key("step_template_id")
        .type(CONDITION_TYPE.DEPEND_ON)
        .value(idStepFromTemplate)
        .build();
  }
}
