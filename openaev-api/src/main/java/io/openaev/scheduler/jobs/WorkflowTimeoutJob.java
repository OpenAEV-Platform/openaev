package io.openaev.scheduler.jobs;

import io.openaev.database.model.Workflow;
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
        workflowEndService.forceCompleteWorkflowByTimeout(workflow);
      } catch (Exception e) {
        log.error(
            "[Chaining] Failed to force-complete expired workflow run {}. Will retry on next cycle.",
            workflow.getId(),
            e);
      }
    }
  }
}
