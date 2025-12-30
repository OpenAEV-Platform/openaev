package io.openaev.service.chaining;

import io.openaev.database.model.CONDITION_TYPE;
import io.openaev.database.model.Condition;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ConditionRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

  public Condition isTimeConditionValid(Condition conditionTemplate, Workflow workflowRun) {
    // Get workflow run date start
    Instant start = workflowRun.getWorkflowCreatedAt();
    if (start == null) {
      start = Instant.now();
    }
    Instant now = new Date().toInstant();
    long value = Long.parseLong(conditionTemplate.getValue());
    Instant goal = start.plus(value, ChronoUnit.MILLIS);
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
      Step stepTemplate, String input, String data, Workflow workflowRun, StepService stepService) {
    List<Condition> conditionTemplate = findAllByStepId(stepTemplate.getId());
    List<Condition> conditionExecution = new ArrayList<>();
    // No condition means direct execution:
    if (conditionTemplate == null || conditionTemplate.isEmpty()) return new ArrayList<>();
    // todo check Condition
    // todo First test time condition
    List<Condition> timeConditions =
        conditionTemplate.stream().filter(this::isTimeCondition).toList();

    for (Condition condition : timeConditions) {
      Condition timeConditionValid = isTimeConditionValid(condition, workflowRun);
      if (timeConditionValid == null) {
        new Thread(
                () -> {
                  queueChainingService.toDeletePushIntoQueueTemplateStepConditionTimeNotValid(
                      stepTemplate, workflowRun, condition, input, stepService);
                },
                "condition time not valid, will be check later:" + stepTemplate.getId())
            .start();
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
      // List of step template depend on that has been run
      List<Step> dependOnStepsRunByTemplateIdAndWorkflowRunId =
          stepService.findAllStepRunByStepTemplateIdAndWorkflowRunId(
              idStepFromTemplate, workflowRun.getId());
      // List of current step template already run
      List<Step> currentStepRunByTemplateIdAndWorkflowRun =
          stepService.findAllStepRunByStepTemplateIdAndWorkflowRunId(
              stepTemplate.getId(), workflowRun.getId());
      if (!dependOnStepsRunByTemplateIdAndWorkflowRunId.isEmpty()
          && currentStepRunByTemplateIdAndWorkflowRun.size() < stepTemplate.getLimitExecution()) {
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
