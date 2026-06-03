package io.openaev.service.chaining;

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
 * to the workflow's simulation have reached a terminal execution status within a sliding time
 * window and compares against the configured maximum attempts. If the limit is reached, further
 * inject execution is denied until older executions fall outside the window.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RateLimitGuardService {

  private final InjectStatusRepository injectStatusRepository;

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

    long count = injectStatusRepository.countTerminalInjectsSince(simulationId, since);

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
}
