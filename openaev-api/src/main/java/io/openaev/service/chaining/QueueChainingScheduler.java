package io.openaev.service.chaining;

import io.openaev.database.model.Condition;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepsDelayQueue;
import io.openaev.rest.exception.ChainingException;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueueChainingScheduler {
  private final StepDelayQueueService stepDelayQueueService;
  private final StepService stepService;

  /**
   * Scheduled method to process delayed steps from the queue.
   *
   * <p>This method runs at a fixed interval (configured via {@code
   * openaev.cron.config.steps.delay.queue.polling.interval}, default 10 seconds). For each step
   * retrieved from the delay queue:
   *
   * <ul>
   *   <li>Check if the step is ready via {@link StepService#ready(Step,
   *       io.openaev.database.model.Workflow, String)}.
   *   <li>If ready, update the delay rate condition using {@link
   *       io.openaev.service.chaining.ConditionService#delayRateTimeCondition(Condition, Instant,
   *       Long)}.
   *   <li>Delete the step from the queue after processing. If the step is not ready, it may be
   *       re-queued inside {@code ready()}.
   * </ul>
   *
   * Exceptions during step readiness evaluation are logged but do not stop the scheduler.
   */
  @Scheduled(fixedDelayString = "${openaev.cron.config.steps.delay.queue.polling.interval:10000}")
  public void processDelayStep() {

    // Retrieve the next delayed step whose goal time is reached
    Optional<StepsDelayQueue> stepDelayOptional = stepDelayQueueService.findNextToProcess();
    stepDelayOptional.ifPresent(
        stepDelay -> {
          Optional<Step> stepReady = Optional.empty();
          try {
            // Check if the step is ready to execute.
            // This may involve evaluating conditions and dependencies.
            stepReady =
                stepService.ready(
                    stepDelay.getStepTemplate(), stepDelay.getWorkflowRun(), stepDelay.getInput());
          } catch (ChainingException e) {
            log.error("Delay consume failed : {}", e.getMessage(), e);
          }
          // If the step is ready (stepReady.isPresent()):
          // - It means 1 execution will be completed.
          // - We wait for the next rate limit interval before executing subsequent actions.
          if (stepReady
              .isPresent()) { // todo add check if workflow contain delay rate limit (means its a
            // chaining event base)

            // Update the delay condition for all steps in the workflow run.
            // This ensures the rate limit is respected for subsequent step executions.
            try {
              stepService.delayRateTimeCondition(
                  stepDelay.getDelayCondition(), Instant.now(), stepDelay.getDelay());
            } catch (IllegalArgumentException e) {
              // Programming error — condition is not of type AFTER
              // Delete anyway to prevent infinite loop
              log.error(
                  "Invalid condition type for delay rate, entry will be discarded. "
                      + "Condition ID: {}, Type: {}",
                  stepDelay.getDelayCondition().getId(),
                  stepDelay.getDelayCondition().getType(),
                  e);
            }
          }
          // Always deleted after treatment, if needed the method ready() will delay the step again
          stepDelayQueueService.deleteStepsDelayQueue(stepDelay);
        });
  }
}
