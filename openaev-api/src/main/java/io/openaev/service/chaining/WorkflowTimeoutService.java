package io.openaev.service.chaining;

import io.openaev.database.model.Workflow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class WorkflowTimeoutService {
  private final WorkflowService workflowService;
  private final StepService stepService;
  private final StepDelayQueueService stepDelayQueueService;

  /**
   * Finds all RUN workflows whose timeout has expired.
   *
   * @return list of expired workflows
   */
  public List<Workflow> findAllExpiredRunWorkflows() {
    return workflowService.findAllExpiredRunWorkflows();
  }

  /**
   * Forces a workflow run to complete due to timeout expiration. Sets the workflow status to END,
   * terminates all active steps (READY or RUN), and removes pending delay queue entries.
   *
   * @param workflowRun the running workflow to force-complete
   */
  @Transactional(rollbackFor = Exception.class)
  public void forceCompleteWorkflow(Workflow workflowRun) {
    log.info("Timeout expired for workflow run {}. Forcing completion.", workflowRun.getId());

    // 1. End all active steps (READY or RUN)
    int terminatedCount = stepService.endActiveStepsByWorkflowId(workflowRun.getId());

    // 2. Remove pending delay queue entries for this workflow run
    stepDelayQueueService.deleteAllByWorkflowRun(workflowRun);

    // 3. Set workflow status to END
    workflowService.endWorkflow(workflowRun);

    log.info(
        "Workflow run {} force-completed. {} active step(s) terminated.",
        workflowRun.getId(),
        terminatedCount);
  }
}
