package io.openaev.scheduler.jobs;

import io.openaev.database.model.Workflow;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.service.chaining.WorkflowEndService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Quartz job that periodically checks for running workflows whose timeout has expired and
 * force-completes them (workflow → END, active steps → END, delay queue entries removed).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class WorkflowTimeoutJob implements Job {

  private final WorkflowEndService workflowEndService;
  private final TenantScopedJobRunner tenantScopedJobRunner;

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    List<Workflow> expiredWorkflows = workflowEndService.findAllExpiredRunWorkflows();
    if (expiredWorkflows.isEmpty()) {
      return;
    }

    log.info(
        "[Chaining] Found {} expired workflow run(s) to force-complete.", expiredWorkflows.size());

    for (Workflow workflow : expiredWorkflows) {
      try {
        // findAllExpiredRunWorkflows spans tenants and this job carries no scope of its own, so the
        // end snapshot resolved its assets with none: every rule of a non-default tenant froze as
        // DELETED_DURING_EXECUTION for assets that still exist. One scope per workflow, taken from
        // the simulation that owns it (workflows carry no tenant column).
        String tenantId = tenantOf(workflow);
        if (tenantId == null) {
          log.warn(
              "[Chaining] Expired workflow run {} has no simulation tenant; force-completing it"
                  + " with no tenant scope, so its end snapshot may resolve nothing.",
              workflow.getId());
          workflowEndService.forceCompleteWorkflowByTimeout(workflow);
        } else {
          tenantScopedJobRunner.runInTenant(
              tenantId, () -> workflowEndService.forceCompleteWorkflowByTimeout(workflow));
        }
      } catch (Exception e) {
        log.error(
            "[Chaining] Failed to force-complete expired workflow run {}. Will retry on next cycle.",
            workflow.getId(),
            e);
      }
    }
  }

  /** Workflows carry no tenant column; the owning simulation does. */
  private static String tenantOf(Workflow workflow) {
    // chk_workflow_simulation_or_scenario guarantees exactly one of the two is set, and both
    // Exercise and Scenario are TenantBase. Reading only the simulation left every
    // scenario-backed workflow unscoped.
    if (workflow.getSimulation() != null && workflow.getSimulation().getTenant() != null) {
      return workflow.getSimulation().getTenant().getId();
    }
    if (workflow.getScenario() != null && workflow.getScenario().getTenant() != null) {
      return workflow.getScenario().getTenant().getId();
    }
    return null;
  }
}
