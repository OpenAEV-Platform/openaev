package io.openaev.scheduler.jobs;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepDelayQueue;
import io.openaev.rest.exception.ChainingException;
import io.openaev.service.chaining.StepDelayQueueService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class QueueChainingJob implements Job {
  private final StepDelayQueueService stepDelayQueueService;
  private final StepService stepService;
  private final WorkflowService workflowService;
  private final TransactionTemplate transactionTemplate;

  /** Periodically processes the next eligible step from the delay queue. */
  @Override
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    // Pop and process inside the same transaction so that if processing fails,
    // the DELETE is rolled back and the entry is not lost.
    transactionTemplate.executeWithoutResult(
        status -> {
          List<StepDelayQueue> stepsDelayQueue = stepDelayQueueService.popNextToProcess();
          if (stepsDelayQueue.isEmpty()) return;

          log.info("QueueChainingJob: processing {} delayed step(s)", stepsDelayQueue.size());

          for (StepDelayQueue stepDelayQueue : stepsDelayQueue) {
            // Guard: ignore if workflow run has already ended (e.g. timeout).
            if (workflowService.isWorkflowEnded(stepDelayQueue.getWorkflowRun().getId())) {
              log.info(
                  "Ignoring step {} because workflow run {} has ended.",
                  stepDelayQueue.getId(),
                  stepDelayQueue.getWorkflowRun().getId());
              log.info(
                  "Deleting all delayed steps for workflow run {} as it has ended.",
                  stepDelayQueue.getWorkflowRun().getId());
              stepDelayQueueService.deleteAllByWorkflowRun(stepDelayQueue.getWorkflowRun());
              continue;
            }

            try {
              Step existingReadyStep = stepDelayQueue.getStepReady();
              if (existingReadyStep != null) {
                // Rate-limit reschedule: re-enqueue the existing READY step directly.
                log.info(
                    "Re-enqueuing existing READY step {} into ready queue",
                    existingReadyStep.getId());
                stepService.enqueueReadySteps(
                    List.of(existingReadyStep), stepDelayQueue.getWorkflowRun());
              } else {
                // Normal delay: create a new READY step from the template.
                stepService.enqueueReadySteps(
                    stepService.createReadySteps(
                        stepDelayQueue.getStepTemplate(),
                        stepDelayQueue.getWorkflowRun(),
                        stepDelayQueue.getInput()),
                    stepDelayQueue.getWorkflowRun());
              }
            } catch (ChainingException e) {
              log.error("Delay consume failed : {}", e.getMessage(), e);
            }
          }
        });
  }
}
