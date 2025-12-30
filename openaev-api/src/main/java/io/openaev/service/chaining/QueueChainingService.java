package io.openaev.service.chaining;

import io.openaev.database.model.Condition;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
@Async
public class QueueChainingService {

  @Async
  @Transactional
  public void toDeletePushIntoQueueTemplateStepConditionTimeNotValid(
      Step stepTemplate,
      Workflow workflowRun,
      Condition condition,
      String input,
      StepService stepService) {
    try {
      log.info(
          "PRODUCE STEP TEMPLATE : {} CONDITION TIME: {} + {} milliseconds",
          stepTemplate.getId(),
          workflowRun.getWorkflowCreatedAt(),
          Long.parseLong(condition.getValue()) * 1000);
      Thread.sleep(Long.parseLong(condition.getValue()) * 1000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    log.info(
        "CONSUME STEP TEMPLATE : {} CONDITION TIME: {} milliseconds",
        stepTemplate.getId(),
        Long.parseLong(condition.getValue()) * 1000);
    stepService.wait(stepTemplate, workflowRun, input);
  }

  @Async
  @Transactional
  public void toDeletePushIntoQueueRunStep(Step stepWait, StepService stepService) {
    /* try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        throw new RuntimeException(e);
    }*/
    stepService.run(stepWait);
  }
}
