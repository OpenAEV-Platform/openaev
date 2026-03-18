package io.openaev.service.chaining;

import io.openaev.database.model.Condition;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepsDelayQueue;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.StepDelayQueueRepository;
import java.time.Instant;
import java.util.Optional;
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
   * <p>Creates a new {@link StepsDelayQueue} entry with the provided step, input, workflow, delay
   * condition, and goal time. The entry is then persisted via the repository.
   *
   * @param stepTemplate the workflow step template to delay
   * @param now the current timestamp when the step is enqueued
   * @param input input data for the step
   * @param delay delay in milliseconds before the step can be processed
   * @param delayCondition the {@link Condition} that controls the delay
   * @param workflowRun the {@link Workflow} instance associated with the step
   * @param goal the target timestamp when the step should be ready to execute
   */
  public void pushStepTemplateIntoStepDelayQueue(
      Step stepTemplate,
      Instant now,
      String input,
      long delay,
      Condition delayCondition,
      Workflow workflowRun,
      Instant goal) {
    log.info(
        "DELAY STEP TEMPLATE : {} CONDITION TIME AFTER: {} + {} milliseconds => Goal: {}",
        stepTemplate.getId(),
        now,
        delay,
        goal);
    StepsDelayQueue stepsDelayQueue =
        StepsDelayQueue.builder()
            .input(input)
            .now(now)
            .goal(goal)
            .delay(delay)
            .delayCondition(delayCondition)
            .stepTemplate(stepTemplate)
            .workflowRun(workflowRun)
            .build();
    stepDelayQueueRepository.save(stepsDelayQueue);
  }

  /**
   * Finds the next step in the delay queue that is ready to be processed.
   *
   * <p>Retrieves the first {@link StepsDelayQueue} entry whose goal time is less than or equal to
   * the current timestamp, ordered by ascending goal time.
   *
   * @return an {@link Optional} containing the next ready {@link StepsDelayQueue}, or empty if none
   *     are ready
   */
  public Optional<StepsDelayQueue> findNextToProcess() {
    return stepDelayQueueRepository.findFirstByGoalLessThanEqualOrderByGoalAsc(Instant.now());
  }

  /**
   * Deletes a {@link StepsDelayQueue} entry from the repository.
   *
   * <p>This should be called after a step has been processed to remove it from the delay queue.
   *
   * @param stepsDelayQueue the queue entry to delete
   */
  public void deleteStepsDelayQueue(StepsDelayQueue stepsDelayQueue) {
    stepDelayQueueRepository.delete(stepsDelayQueue);
  }
}
