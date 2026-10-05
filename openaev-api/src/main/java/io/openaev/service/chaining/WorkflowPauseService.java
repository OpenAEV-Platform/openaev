package io.openaev.service.chaining;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.rest.exception.ChainingException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkflowPauseService {

  private final WorkflowService workflowService;
  private final WorkflowResumeService workflowResumeService;
  private final StepDelayQueueService stepDelayQueueService;
  private final StepService stepService;
  private final EntityManager entityManager;

  @Transactional(rollbackFor = Exception.class)
  public void pauseSimulationWorkflowRuns(String simulationId) {
    if (simulationId == null) {
      return;
    }
    // Current execution only: a legacy database may still hold older runs of the simulation.
    Optional<Workflow> currentRun = findCurrentRunWithStatus(simulationId, WorkflowStatus.RUN);
    if (currentRun.isEmpty()) {
      return;
    }
    Workflow workflowRun = currentRun.get();
    workflowRun.setStatus(WorkflowStatus.STOP);
    workflowRun.setPauseAt(Instant.now());
    stepDelayQueueService.nullifyGoalsByWorkflowRun(workflowRun);
    workflowService.saveWorkflowRun(workflowRun);
  }

  @Transactional(rollbackFor = Exception.class)
  public boolean resumeSimulationWorkflowRuns(String simulationId) throws ChainingException {
    if (simulationId == null) {
      return false;
    }
    Optional<Workflow> currentRun = findCurrentRunWithStatus(simulationId, WorkflowStatus.STOP);
    if (currentRun.isEmpty()) {
      return false;
    }
    Workflow pausedRun = currentRun.get();
    // RUN is committed on its own before any READY event is published (see
    // WorkflowResumeService): published first, an event read against the still-STOP status would
    // be dropped for good.
    if (!workflowResumeService.resumeWorkflowRunIsolated(
        TxCtx.forTenant(pausedRun.getSimulation().getTenant().getId()),
        pausedRun.getId(),
        Instant.now())) {
      return false;
    }
    // This transaction loaded the run before that commit: reload it, or evaluation below would
    // still see it STOP and a later flush could write the stale state back.
    entityManager.refresh(pausedRun);
    List<Step> readySteps =
        stepService.findAllStepsByWorkflowRunIdAndStatus(pausedRun.getId(), StepStatus.READY);
    stepService.enqueueReadySteps(readySteps, pausedRun);
    pausedRun = workflowService.evaluateWorkflowProgress(pausedRun);
    return pausedRun.getStatus() == WorkflowStatus.END;
  }

  private Optional<Workflow> findCurrentRunWithStatus(String simulationId, WorkflowStatus status) {
    return workflowService
        .findCurrentWorkflowExecutionBySimulationId(simulationId)
        .filter(run -> run.getStatus() == status);
  }
}
