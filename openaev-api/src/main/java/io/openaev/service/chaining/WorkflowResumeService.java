package io.openaev.service.chaining;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.WorkflowRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated STOP → RUN commit of a paused workflow run, orchestrated by {@link
 * WorkflowPauseService}. A separate bean so the {@code REQUIRES_NEW} boundary goes through the
 * Spring proxy.
 */
@Service
@RequiredArgsConstructor
public class WorkflowResumeService {

  private final WorkflowRepository workflowRepository;
  private final StepDelayQueueService stepDelayQueueService;

  /**
   * Resumes a paused (STOP) workflow run in its OWN transaction ({@link Propagation#REQUIRES_NEW}):
   * back to RUN, paused duration accumulated, delay goals shifted by it. Committed before the
   * caller publishes any READY event: a ready consumer still reading STOP would drop the event, and
   * the batch hash committed with the step would then keep re-evaluation from republishing it.
   *
   * <p>The {@code ctx} argument is load-bearing even though this body never reads it: {@code
   * REQUIRES_NEW} suspends the caller's transaction and its tenant scope, the tenant aspect sets it
   * back from this parameter (see {@link WorkflowService#writeAllowlistScopeIsolated}).
   *
   * @return {@code false} when the run is no longer STOP, so nothing was resumed
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public boolean resumeWorkflowRunIsolated(TxCtx ctx, String workflowRunId, Instant resumeAt) {
    Optional<Workflow> stoppedRun =
        workflowRepository.findByIdAndStatus(workflowRunId, WorkflowStatus.STOP);
    if (stoppedRun.isEmpty()) {
      return false;
    }
    Workflow workflowRun = stoppedRun.get();
    Instant pausedAt = workflowRun.getPauseAt();
    if (pausedAt != null) {
      long pauseDeltaSeconds = Math.max(0L, Duration.between(pausedAt, resumeAt).getSeconds());
      workflowRun.setPauseSecond(workflowRun.getPauseSecond() + pauseDeltaSeconds);
    }
    workflowRun.setPauseAt(null);
    workflowRun.setStatus(WorkflowStatus.RUN);
    stepDelayQueueService.recalculateGoalsOnResume(workflowRun, resumeAt, pausedAt);
    workflowRepository.save(workflowRun);
    return true;
  }
}
