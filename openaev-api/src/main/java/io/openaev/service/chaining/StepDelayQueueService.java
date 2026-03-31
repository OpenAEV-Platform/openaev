package io.openaev.service.chaining;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepDelayQueue;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.StepDelayQueueRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing delayed execution of workflow steps using a delay queue.
 *
 * <p>This service allows steps to be pushed into a delay queue with a specific delay, retrieved
 * when their goal time has been reached, and deleted once processed. It interacts with the {@link
 * StepDelayQueueRepository} for persistence.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class StepDelayQueueService {
  private final StepDelayQueueRepository stepDelayQueueRepository;

  /**
   * Pushes a step template into the delay queue.
   *
   * <p>Creates a new {@link StepDelayQueue} entry with the provided step, input, workflow, delay
   * condition, and goal time. The entry is then persisted via the repository.
   *
   * @param stepTemplate the workflow step template to delay
   * @param now the current timestamp when the step is enqueued
   * @param input input data for the step
   * @param delay delay in milliseconds before the step can be processed
   * @param workflowRun the {@link Workflow} instance associated with the step
   * @param goal the target timestamp when the step should be ready to execute
   */
  public void pushStepTemplateIntoStepDelayQueue(
      Step stepTemplate,
      Instant now,
      String input,
      long delay,
      Workflow workflowRun,
      Instant goal) {
    log.debug(
        "Delay step template: {} condition time after: {} + {} milliseconds => goal: {}",
        stepTemplate.getId(),
        now,
        delay,
        goal);
    StepDelayQueue stepDelayQueue =
        StepDelayQueue.builder()
            .input(input)
            .now(now)
            .goal(goal)
            .delay(delay)
            .stepTemplate(stepTemplate)
            .workflowRun(workflowRun)
            .build();
    stepDelayQueueRepository.save(stepDelayQueue);
  }

  /**
   * Atomically retrieves and removes the oldest eligible entry per workflow run from the delay
   * queue.
   *
   * <p>A step is eligible when its scheduled goal time has been reached. Atomicity is guaranteed at
   * the database level via {@code DELETE ... RETURNING}. {@code synchronized} provides an
   * additional safeguard for single-pod concurrency.
   *
   * @return the oldest eligible {@link StepDelayQueue} per workflow run, or an empty list
   */
  public synchronized List<StepDelayQueue> popNextToProcess() {
    return stepDelayQueueRepository.popNextPerWorkflowRun();
  }
}
