package io.openaev.service.chaining;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.rest.exception.ChainingException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    Instant pausedAt = Instant.now();
    List<Workflow> workflowRuns = workflowService.findWorkflowRunBySimulationId(simulationId);

    for (Workflow workflowRun : workflowRuns) {
      workflowRun.setStatus(WorkflowStatus.STOP);
      workflowRun.setPauseAt(pausedAt);
      stepDelayQueueService.nullifyGoalsByWorkflowRun(workflowRun);
      workflowService.saveWorkflowRun(workflowRun);
    }
  }

  @Transactional(rollbackFor = Exception.class)
  public boolean resumeSimulationWorkflowRuns(String simulationId) throws ChainingException {
    if (simulationId == null) {
      return false;
    }
    Instant resumeAt = Instant.now();
    List<Workflow> pausedRuns = workflowService.findWorkflowStoppedBySimulationId(simulationId);

    for (Workflow pausedRun : pausedRuns) {
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
      if (readySteps.isEmpty()) {
        pausedRun = workflowService.evaluateWorkflowProgress(pausedRun);
        if (pausedRun.getStatus() == WorkflowStatus.END) {
          return true;
        }
      }
    }
    return false;
  }
}
