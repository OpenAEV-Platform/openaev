package io.openaev.service.chaining;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepActionClass;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.database.repository.StepRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Guards workflow execution against exceeding a configured rate limit.
 *
 * <p>When rate limiting is enabled on a workflow run, this service counts how many injects linked
 * to the workflow's simulation have been launched (i.e. have a tracking sent date) within a sliding
 * time window and compares against the configured maximum attempts. If the limit is reached,
 * further inject execution is denied until older launches fall outside the window.
 *
 * <p>An inject step readied but not yet run has no inject with a tracking sent date yet, so it is
 * counted from the READY inject-execution steps of the run: otherwise a step readied in one
 * evaluation cycle and still queued at the next one was invisible to the limit, and the engine
 * could admit more than the configured maximum.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SimulationRateLimitService {

  private final InjectStatusRepository injectStatusRepository;
  private final StepRepository stepRepository;
  private final StepDelayQueueService stepDelayQueueService;

  /**
   * Determines whether a new inject execution is allowed for the given workflow run based on its
   * rate limit configuration.
   *
   * @param workflowRun the workflow run to evaluate
   * @param pendingCount number of batches accepted in the current call but not yet saved as READY
   *     steps (a saved READY step is counted from the database)
   * @return {@code true} if execution is allowed, {@code false} if the rate limit has been reached
   */
  public boolean isExecutionAllowed(Workflow workflowRun, int pendingCount) {
    if (!workflowRun.isRateLimitEnabled()) {
      return true;
    }

    if (workflowRun.getMaxAttempts() == null || workflowRun.getMaxTemporalRateSeconds() == null) {
      log.warn(
          "[Chaining] Rate limit is enabled for workflow {} but maxAttempts or maxTemporalRateSeconds is null. Failing open.",
          workflowRun.getId());
      return true;
    }

    Instant since =
        Instant.now().minus(workflowRun.getMaxTemporalRateSeconds(), ChronoUnit.SECONDS);
    String simulationId = workflowRun.getSimulation().getId();

    long readiedNotRun =
        stepRepository.countByWorkflowRunAndStatusAndAction(
            workflowRun.getId(), StepStatus.READY, StepActionClass.INJECT_EXECUTION);
    long count =
        injectStatusRepository.countLaunchedInjectsSince(simulationId, since)
            + readiedNotRun
            + pendingCount;

    if (count >= workflowRun.getMaxAttempts()) {
      log.info(
          "[Chaining] Rate limit reached for workflow {} ({}/{} in {}s window, including {} readied"
              + " not run and {} pending)",
          workflowRun.getId(),
          count,
          workflowRun.getMaxAttempts(),
          workflowRun.getMaxTemporalRateSeconds(),
          readiedNotRun,
          pendingCount);
      return false;
    }

    return true;
  }

  /**
   * Checks if the rate limit has been reached and, if so, pushes the step template into the delay
   * queue for later retry.
   *
   * @param stepTemplate the step template to potentially delay
   * @param input the input data for the step
   * @param workflowRun the workflow run to evaluate
   * @param pendingCount number of steps already scheduled in the current evaluation cycle but not
   *     yet reflected in the database
   * @return {@code true} if the step was delayed (rate limit reached), {@code false} if execution
   *     can proceed
   */
  public boolean delayIfRateLimitReached(
      Step stepTemplate, String input, Workflow workflowRun, int pendingCount) {
    if (workflowRun == null || isExecutionAllowed(workflowRun, pendingCount)) {
      return false;
    }
    long backoffMillis =
        workflowRun.getMaxTemporalRateSeconds() != null
            ? workflowRun.getMaxTemporalRateSeconds() * 1000L
            : 60_000L;
    Instant now = Instant.now();
    stepDelayQueueService.pushStepTemplateIntoStepDelayQueue(
        stepTemplate, now, input, backoffMillis, workflowRun, now.plusMillis(backoffMillis));
    log.info(
        "[Chaining] Rate limit reached — delaying template {} for workflow {} (backoff: {}ms)",
        stepTemplate.getId(),
        workflowRun.getId(),
        backoffMillis);
    return true;
  }
}
