package io.openaev.service.chaining;

import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.InjectStatusRepository;
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
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SimulationRateLimitService {

  private final InjectStatusRepository injectStatusRepository;
  private final StepDelayQueueService stepDelayQueueService;

  /**
   * Determines whether a new inject execution is allowed for the given workflow run based on its
   * rate limit configuration.
   *
   * @param workflowRun the workflow run to evaluate
   * @return {@code true} if execution is allowed, {@code false} if the rate limit has been reached
   */
  public boolean isExecutionAllowed(Workflow workflowRun) {
    if (!workflowRun.isRateLimitEnabled()) {
      return true;
    }

    if (workflowRun.getMaxAttempts() == null || workflowRun.getMaxTemporalRateSeconds() == null) {
      log.warn(
          "Rate limit is enabled for workflow {} but maxAttempts or maxTemporalRateSeconds is null. Failing open.",
          workflowRun.getId());
      return true;
    }

    Instant since =
        Instant.now().minus(workflowRun.getMaxTemporalRateSeconds(), ChronoUnit.SECONDS);
    String simulationId = workflowRun.getSimulation().getId();

    long count = injectStatusRepository.countLaunchedInjectsSince(simulationId, since);

    if (count >= workflowRun.getMaxAttempts()) {
      log.info(
          "Rate limit reached for workflow {} ({}/{} in {}s window)",
          workflowRun.getId(),
          count,
          workflowRun.getMaxAttempts(),
          workflowRun.getMaxTemporalRateSeconds());
      return false;
    }

    return true;
  }

  /**
   * Check that a rate limit has been reached and re-schedule the step if necessary. Returns true if
   * the step was re-scheduled, false otherwise.
   *
   * @param stepReady the step to check
   * @param workflowRun the workflow being run
   * @return true if the step was re-scheduled, false otherwise.
   */
  public boolean requeueIfRateLimitReached(Step stepReady, Workflow workflowRun) {
    // Guard: rate limit — re-schedule the step if the workflow has reached its rate limit.
    if (workflowRun != null && !this.isExecutionAllowed(workflowRun)) {
      long backoffSeconds =
          workflowRun.getMaxTemporalRateSeconds() != null
              ? workflowRun.getMaxTemporalRateSeconds()
              : 60L;
      // Increment the rate-limit count on the step itself so it propagates through the delay
      // queue and can be surfaced as an INFO trace when the inject is eventually executed.
      stepReady.setRateLimitCount(stepReady.getRateLimitCount() + 1);
      stepDelayQueueService.reschedule(stepReady, backoffSeconds);
      return true;
    }
    return false;
  }
}
