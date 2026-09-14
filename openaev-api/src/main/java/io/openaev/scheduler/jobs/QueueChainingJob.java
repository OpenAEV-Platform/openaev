package io.openaev.scheduler.jobs;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepDelayQueue;
import io.openaev.database.model.Workflow;
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

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class QueueChainingJob implements Job {
  private final StepDelayQueueService stepDelayQueueService;
  private final StepService stepService;
  private final WorkflowService workflowService;
  private final TenantScopedTransaction tenantTx;

  /** Periodically processes the next eligible step from the delay queue. */
  @Override
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    // Pop and process inside the same transaction so that if processing fails, the DELETE is
    // rolled back and the entry is not lost. The transaction is opened through the tenant-aware
    // primitive rather than a raw TransactionTemplate, which is what background jobs are required
    // to use, and it starts at allTenants() because popNextPerWorkflowRun legitimately spans
    // tenants. Each entry then narrows the scope to its own tenant below.
    tenantTx.execute(
        TxCtx.allTenants(),
        () -> {
          List<StepDelayQueue> stepsDelayQueue = stepDelayQueueService.popNextToProcess();
          if (stepsDelayQueue.isEmpty()) return;

          log.info(
              "[Chaining] QueueChainingJob: processing {} delayed step(s)", stepsDelayQueue.size());

          for (StepDelayQueue stepDelayQueue : stepsDelayQueue) {
            // popNextPerWorkflowRun spans workflow runs and therefore tenants, while this job runs
            // in ONE transaction so a processing failure rolls the DELETE back. Both scopes are
            // moved per entry instead: the v2 GUC for the reads the inspector rewrites, and the v1
            // thread-local for the tenant TenantBaseListener stamps on a write.
            //
            // Without this the whole transaction has no scope, and createReadySteps reaches
            // ScopeService.getValidAssets -> assetService.assets(ids) on the activated assets
            // table. That returns empty, expandTargetBatches takes its "scope resolves to no asset"
            // branch, and the delayed inject fires with no per-asset target. No exception, no log.
            String tenantId = tenantOf(stepDelayQueue);
            if (tenantId == null) {
              // Processed anyway rather than skipped: dropping a queued step would be the same
              // silent loss this scoping exists to prevent, and a run with no simulation has no
              // tenant to scope to. The warning is what makes the degraded read visible.
              log.warn(
                  "[Chaining] Delayed step {} has no simulation tenant, so it is processed with no"
                      + " tenant scope: any read of an activated table will come back empty.",
                  stepDelayQueue.getId());
            } else {
              TenantContext.setCurrentTenant(tenantId);
              tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantId));
            }

            // Guard: ignore if workflow run has already ended (e.g. timeout).
            if (workflowService.isWorkflowEnded(stepDelayQueue.getWorkflowRun().getId())) {
              log.info(
                  "[Chaining] Ignoring step {} because workflow run {} has ended.",
                  stepDelayQueue.getId(),
                  stepDelayQueue.getWorkflowRun().getId());
              log.info(
                  "[Chaining] Deleting all delayed steps for workflow run {} as it has ended.",
                  stepDelayQueue.getWorkflowRun().getId());
              stepDelayQueueService.deleteAllByWorkflowRun(stepDelayQueue.getWorkflowRun());
              continue;
            }

            try {
              // Create new READY step(s) from the template.
              // Rate limiting is handled inside createReadySteps at the batch level:
              // batches that exceed the rate limit are re-pushed into the delay queue.
              List<Step> readySteps =
                  stepService.createReadySteps(
                      stepDelayQueue.getStepTemplate(),
                      stepDelayQueue.getWorkflowRun(),
                      stepDelayQueue.getInput(),
                      0);

              stepService.enqueueReadySteps(readySteps, stepDelayQueue.getWorkflowRun());
            } catch (ChainingException e) {
              log.error("[Chaining] Delay consume failed : {}", e.getMessage(), e);
            }
          }
          TenantContext.clearCurrentTenant();
        });
  }

  /**
   * The tenant a delayed step belongs to. {@code workflows} carries no tenant column of its own;
   * its owner does. {@code chk_workflow_simulation_or_scenario} allows exactly one of a simulation
   * or a scenario, and both are {@code TenantBase}, so both have to be read.
   */
  private static String tenantOf(StepDelayQueue stepDelayQueue) {
    // chk_workflow_simulation_or_scenario guarantees exactly one of the two is set, and both
    // Exercise and Scenario are TenantBase. Reading only the simulation left every
    // scenario-backed workflow unscoped.
    Workflow run = stepDelayQueue.getWorkflowRun();
    if (run == null) {
      return null;
    }
    if (run.getSimulation() != null && run.getSimulation().getTenant() != null) {
      return run.getSimulation().getTenant().getId();
    }
    if (run.getScenario() != null && run.getScenario().getTenant() != null) {
      return run.getScenario().getTenant().getId();
    }
    return null;
  }
}
