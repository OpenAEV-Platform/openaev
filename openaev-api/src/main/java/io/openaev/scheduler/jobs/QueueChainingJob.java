package io.openaev.scheduler.jobs;

import io.openaev.aop.BypassRls;
import io.openaev.database.model.StepDelayQueue;
import io.openaev.rest.exception.ChainingException;
import io.openaev.service.chaining.StepDelayQueueService;
import io.openaev.service.chaining.StepService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class QueueChainingJob implements Job {
  private final StepDelayQueueService stepDelayQueueService;
  private final StepService stepService;

  /** Periodically processes the next eligible step from the delay queue. */
  @Override
  @BypassRls
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    // Retrieve and delete the next delayed step whose goal time is reached
    List<StepDelayQueue> stepsDelayQueue = stepDelayQueueService.popNextToProcess();
    if (stepsDelayQueue.isEmpty()) return;

    for (StepDelayQueue stepDelayQueue : stepsDelayQueue) {
      try {
        stepService.enqueueReadySteps(
            stepService.createReadySteps(
                stepDelayQueue.getStepTemplate(),
                stepDelayQueue.getWorkflowRun(),
                stepDelayQueue.getInput()),
            stepDelayQueue.getWorkflowRun());
      } catch (ChainingException e) {
        log.error("Delay consume failed : {}", e.getMessage(), e);
      }
    }
  }
}
