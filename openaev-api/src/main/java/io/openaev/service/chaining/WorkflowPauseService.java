package io.openaev.service.chaining;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.rest.exception.ChainingException;
import java.time.Duration;
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
  private final StepDelayQueueService stepDelayQueueService;
  private final StepService stepService;

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
    Instant resumeAt = Instant.now();
    Instant pausedAt = pausedRun.getPauseAt();
    if (pausedAt != null) {
      long pauseDeltaSeconds = Math.max(0L, Duration.between(pausedAt, resumeAt).getSeconds());
      pausedRun.setPauseSecond(pausedRun.getPauseSecond() + pauseDeltaSeconds);
    }
    pausedRun.setPauseAt(null);
    pausedRun.setStatus(WorkflowStatus.RUN);
    stepDelayQueueService.recalculateGoalsOnResume(pausedRun, resumeAt, pausedAt);
    pausedRun = workflowService.saveWorkflowRun(pausedRun);
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
