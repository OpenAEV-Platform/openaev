package io.openaev.service.chaining;

import io.openaev.database.model.*;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import io.openaev.utils.ExecutionTraceUtils;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
  private final ExerciseService simulationService;
  private final InjectService injectService;
  private final InjectStatusService injectStatusService;
  private final ResultsMetricCollector resultsMetricCollector;

  private static final Set<ExecutionStatus> ACTIVE_INJECT_STATUSES =
      Set.of(ExecutionStatus.QUEUING, ExecutionStatus.EXECUTING, ExecutionStatus.PENDING);

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
   * terminates all active steps (READY or RUN), removes pending delay queue entries, completes
   * active injects (set to SUCCESS), and finishes the associated simulation.
   *
   * @param workflowRun the running workflow to force-complete
   */
  @Transactional(rollbackFor = Exception.class)
  public void forceCompleteWorkflow(Workflow workflowRun) {
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
    workflowService.endWorkflow(workflowRun);

    // 4. Stop active injects and finish the associated simulation
    Exercise simulation = workflowRun.getSimulation();
    if (simulation != null) {
      int stoppedInjects = stopActiveInjects(simulation.getId());

      simulation.setStatus(ExerciseStatus.FINISHED);
      simulation.setEnd(Instant.now());
      simulationService.saveSimulation(simulation);

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
  private int stopActiveInjects(String simulationId) {
    List<Inject> injects = injectService.findBySimulationId(simulationId);
    int stoppedCount = 0;
    for (Inject inject : injects) {
      if (inject.getStatus().isPresent()
          && ACTIVE_INJECT_STATUSES.contains(inject.getStatus().get().getName())) {
        InjectStatus status = inject.getStatus().get();
        ExecutionTraceUtils.addSimulationTimeoutTrace(status);
        status.setName(ExecutionStatus.ERROR);
        status.setTrackingEndDate(Instant.now());
        injectStatusService.save(status);
        stoppedCount++;
      }
    }
    return stoppedCount;
  }
}
