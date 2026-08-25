package io.openaev.service.chaining;

import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import io.openaev.utils.ExecutionTraceUtils;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class WorkflowEndService {
  private final StepService stepService;
  private final StepDelayQueueService stepDelayQueueService;
  private final ExerciseRepository exerciseRepository;
  private final InjectService injectService;
  private final InjectStatusService injectStatusService;
  private final ResultsMetricCollector resultsMetricCollector;
  private final WorkflowRepository workflowRepository;
  private final ScopeSnapshotService scopeSnapshotService;

  private static final Set<ExecutionStatus> ACTIVE_INJECT_STATUSES =
      Set.of(ExecutionStatus.QUEUING, ExecutionStatus.EXECUTING, ExecutionStatus.PENDING);

  public enum WORKFLOW_END_CAUSE {
    TIMEOUT,
    CANCELED,
    DELETED,
    NO_MORE_PROGRESS
  }

  /**
   * Forces a workflow run to complete due to timeout expiration. Sets the workflow status to END,
   * terminates all active steps (READY or RUN), removes pending delay queue entries, completes
   * active injects (set to SUCCESS), and finishes the associated simulation.
   *
   * @param workflowRun the running workflow to force-complete
   */
  @Transactional(rollbackFor = Exception.class)
  public void forceCompleteWorkflowByTimeout(Workflow workflowRun) {
    log.info(
        "[Chaining] Timeout expired for workflow run {}. Forcing completion.", workflowRun.getId());
    // Telemetry: the timeout safety policy actually fired (complements the
    // safety_timeout_configured configuration metric).
    resultsMetricCollector.recordWorkflowTimeoutTriggered();

    // 1. End all active steps (READY or RUN)
    int terminatedCount = stepService.endActiveStepsByWorkflowId(workflowRun.getId());

    // 2. Remove pending delay queue entries for this workflow run
    stepDelayQueueService.deleteAllByWorkflowRun(workflowRun);

    // 3. Set workflow status to END
    endWorkflow(workflowRun, WORKFLOW_END_CAUSE.TIMEOUT);

    // 4. Stop active injects and finish the associated simulation
    Exercise simulation = workflowRun.getSimulation();
    if (simulation != null) {
      int stoppedInjects = stopActiveInjects(simulation.getId(), WORKFLOW_END_CAUSE.TIMEOUT);

      simulation.setStatus(ExerciseStatus.FINISHED);
      simulation.setEnd(Instant.now());
      exerciseRepository.save(simulation);

      log.info(
          "[Chaining] Simulation {} finished due to workflow timeout. {} active inject(s) stopped.",
          simulation.getId(),
          stoppedInjects);
    }

    log.info(
        "[Chaining] Workflow run {} force-completed. {} active step(s) terminated.",
        workflowRun.getId(),
        terminatedCount);
  }

  /**
   * Completes all active injects (QUEUING, EXECUTING, PENDING) for the given simulation by setting
   * their status to SUCCESS with a tracking end date.
   *
   * @param simulationId the simulation ID
   * @return the number of injects completed
   */
  public int stopActiveInjects(String simulationId, WORKFLOW_END_CAUSE cause) {
    if (cause == WORKFLOW_END_CAUSE.DELETED) return 0;

    List<Inject> injects = injectService.findBySimulationId(simulationId);
    int stoppedCount = 0;
    for (Inject inject : injects) {
      if (inject.getStatus().isPresent()
          && ACTIVE_INJECT_STATUSES.contains(inject.getStatus().get().getName())) {
        InjectStatus status = inject.getStatus().get();
        switch (cause) {
          case TIMEOUT -> ExecutionTraceUtils.addSimulationTimeoutTrace(status);
          case CANCELED -> ExecutionTraceUtils.addSimulationInterruptedTrace(status);
        }
        status.setName(ExecutionStatus.ERROR);
        status.setTrackingEndDate(Instant.now());
        injectStatusService.save(status);
        stoppedCount++;
      }
    }
    return stoppedCount;
  }

  private boolean hasActiveInjects(String simulationId) {
    List<Inject> injects = injectService.findBySimulationId(simulationId);
    for (Inject inject : injects) {
      if (inject.getStatus().isPresent()
          && ACTIVE_INJECT_STATUSES.contains(inject.getStatus().get().getName())) {
        return true;
      }
    }
    return false;
  }

  public void stopSimulationByEndWorkflow(Workflow workflowRun) {
    Exercise simulation = workflowRun.getSimulation();
    if (simulation != null && workflowRun.getStatus().equals(WorkflowStatus.END)) {
      int countInjects = 0;

      if (hasActiveInjects(simulation.getId()))
        countInjects = stopActiveInjects(simulation.getId(), WORKFLOW_END_CAUSE.CANCELED);

      simulation.setStatus(ExerciseStatus.FINISHED);
      simulation.setEnd(Instant.now());
      exerciseRepository.save(simulation);

      log.info(
          "[Chaining] Simulation {} finished due to workflow end. {} active inject(s) stopped.",
          simulation.getId(),
          countInjects);
    }
  }

  /**
   * Single END transition for a RUN workflow: sets the status and freezes the end scope snapshot
   * exactly once (re-running the launch-time resolution). Idempotent - a run already ended is left
   * untouched so the frozen end photo is never overwritten. See ADR-006.
   *
   * @param workflowRun the RUN workflow reaching END/STOP
   */
  void markWorkflowEnded(Workflow workflowRun, WorkflowEndService.WORKFLOW_END_CAUSE cause) {
    if (WorkflowStatus.END.equals(workflowRun.getStatus())) {
      return;
    }
    workflowRun.setStatus(WorkflowStatus.END);
    scopeSnapshotService.freezeEnd(workflowRun);

    switch (cause) {
      case NO_MORE_PROGRESS -> stopSimulationByEndWorkflow(workflowRun);
    }
  }

  /**
   * Sets the workflow status to END and persists it.
   *
   * @param workflowRun the running workflow to end
   */
  public void endWorkflow(Workflow workflowRun, WorkflowEndService.WORKFLOW_END_CAUSE cause) {
    markWorkflowEnded(workflowRun, cause);
    workflowRepository.save(workflowRun);
  }

  /**
   * Finds all RUN workflows whose timeout has expired.
   *
   * @return list of expired workflows
   */
  public List<Workflow> findAllExpiredRunWorkflows() {
    List<String> workflowIds = workflowRepository.findAllExpiredRunWorkflowIds();
    if (workflowIds.isEmpty()) return Collections.emptyList();
    return workflowRepository.findAllByIdWithScopeRules(workflowIds);
  }
}
