package io.openaev.service.chaining;

import io.openaev.api.chaining.ActionStep;
import io.openaev.database.model.Condition;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.rest.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueueChainingScheduler {
  private final StepService stepService;

  @Scheduled(cron = "0 * * * * *")
  public void toDeleteScheduleCheckOutput() {
    // FOR QUEUE
    // Take a step run
    List<Step> stepsRun = stepService.findAllStepRun(); // TODO: replace by findbyid following update event consumption
    for (Step stepRun : stepsRun) {
      ActionStep actionStep = stepService.factoryAction(stepRun.getStepAction());
      if (actionStep == null) throw new BadRequestException("action step is null");

      Step stepUpdated = actionStep.update(stepRun);
      if (stepUpdated != null) {
        stepService.saveStep(stepUpdated);
        // GET STEP TEMPLATE
        Step stepTemplateCurrent =
            stepService.findStepTemplateById(stepRun.getStepTemplate().getId());
        Workflow workflowTemplate = stepTemplateCurrent.getWorkflow();
        List<Step> stepsTemplate =
            stepService.findAllStepTemplateByWorkflow(workflowTemplate.getId());

        // FIND OTHER STEP WHO NEED INPUT FROM THIS STEP
        List<Step> nextStepToExecute = new ArrayList<>();
        for (Step stepTemplate : stepsTemplate) {
          List<Condition> conditions =
              stepService.conditionService.findAllByStepId(stepTemplate.getId());
          for (Condition conditionTemplate : conditions) {
            if (conditionTemplate.getStepFrom() != null
                && conditionTemplate
                    .getStepFrom()
                    .getId()
                    .equals(stepRun.getStepTemplate().getId())) {
              nextStepToExecute.add(stepTemplate);
            }
          }
        }

        for (Step stepTemplate : nextStepToExecute) {
          stepService.wait(stepTemplate, stepRun.getWorkflow(), null);
        }
      }
    }
  }
}
