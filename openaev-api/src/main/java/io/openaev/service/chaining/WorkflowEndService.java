package io.openaev.service.chaining;

import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.WorkflowStateRepository;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.service.attackpath.ingestion.AttackPathExecutionIngestionService;
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
  private final AssetAgentJobRepository assetAgentJobRepository;
  private final WorkflowStateRepository workflowStateRepository;
  private final AttackPathExecutionIngestionService attackPathExecutionIngestionService;

  private static final Set<ExecutionStatus> ACTIVE_INJECT_STATUSES =
      Set.of(ExecutionStatus.QUEUING, ExecutionStatus.EXECUTING, ExecutionStatus.PENDING);

  public enum WORKFLOW_END_CAUSE {
    TIMEOUT,
    CANCELED,
    CANCELED_BY_SIMULATION_DELETION,
    DELETED_BY_RESET_SIMULATION,
    DELETED_BY_SIMULATION_DELETION,
    NO_MORE_PROGRESS
  }

  public static final Set<WORKFLOW_END_CAUSE> WORKFLOW_END_CAUSE_BY_DELETION =
      Set.of(
          WORKFLOW_END_CAUSE.DELETED_BY_RESET_SIMULATION,
          WORKFLOW_END_CAUSE.CANCELED_BY_SIMULATION_DELETION,
          WORKFLOW_END_CAUSE.DELETED_BY_SIMULATION_DELETION);

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

    endWorkflow(workflowRun, WORKFLOW_END_CAUSE.TIMEOUT);
  }

  /**
   * Completes all active injects (QUEUING, EXECUTING, PENDING) for the given simulation by setting
   * their status to SUCCESS with a tracking end date.
   *
   * @param simulationId the simulation ID
   * @param cause the reason the workflow is ending; skipped entirely for {@code
   *     DELETED_SIMULATION_RESET} and {@code DELETED_SIMULATION}
   */
  public void stopActiveInjects(String simulationId, WORKFLOW_END_CAUSE cause) {
    if (cause == WORKFLOW_END_CAUSE.DELETED_BY_RESET_SIMULATION
        || cause == WORKFLOW_END_CAUSE.DELETED_BY_SIMULATION_DELETION) return;

    List<Inject> injects = injectService.findBySimulationId(simulationId);
    int stoppedCount = 0;
    for (Inject inject : injects) {
      if (inject.getStatus().isPresent()
          && ACTIVE_INJECT_STATUSES.contains(inject.getStatus().get().getName())) {
        InjectStatus status = inject.getStatus().get();
        switch (cause) {
          case TIMEOUT -> ExecutionTraceUtils.addSimulationTimeoutTrace(status);
          case CANCELED, CANCELED_BY_SIMULATION_DELETION ->
              ExecutionTraceUtils.addSimulationInterruptedTrace(status);
          /* NO_MORE_PROGRESS with active inject should never happen,
           *  NO_MORE_PROGRESS:
           *       - All Step with status END (All output that an inject can receive has been received)
           *       - No step template DELAY (in the delay queue)*/
          case NO_MORE_PROGRESS -> {
            log.error(
                "[Chaining] Simulation {} stopped due to workflow {}. But inject {} is still active.",
                simulationId,
                cause.name(),
                inject.getId());
            ExecutionTraceUtils.addSimulationNoMoreProgressTrace(status);
          }
        }
        status.setName(ExecutionStatus.ERROR);
        status.setTrackingEndDate(Instant.now());
        injectStatusService.save(status);
        stoppedCount++;
      }
    }

    log.info(
        "[Chaining] Stop {} active inject(s) of the simulation {} due to workflow {}.",
        stoppedCount,
        simulationId,
        cause.name());
  }

  /**
   * Finishes the simulation associated to a RUN-workflow when it just reached END: sets it to
   * FINISHED. No-op if the workflow has no simulation, is not yet END, or {@code cause} is {@code
   * CANCELED} (the simulation was already stopped by the user in that case).
   *
   * @param workflowRun the workflow whose simulation should be finished
   * @param cause the reason the workflow ended
   */
  public void stopSimulationByEndWorkflow(Workflow workflowRun, WORKFLOW_END_CAUSE cause) {
    if (cause == WORKFLOW_END_CAUSE.CANCELED
        || cause == WORKFLOW_END_CAUSE.CANCELED_BY_SIMULATION_DELETION) return;
    Exercise simulation = workflowRun.getSimulation();
    if (simulation != null && workflowRun.getStatus().equals(WorkflowStatus.END)) {

      simulation.setStatus(ExerciseStatus.FINISHED);
      simulation.setEnd(Instant.now());
      exerciseRepository.save(simulation);

      log.info(
          "[Chaining] Simulation {} finished due to workflow {}.",
          simulation.getId(),
          cause.name());
    }
  }

  /**
   * Single END transition for a RUN workflow: sets the status and freezes the end scope snapshot
   * exactly once (re-running the launch-time resolution). Idempotent - a run already ended is left
   * untouched so the frozen end photo is never overwritten. See ADR-006.
   *
   * <p>For {@code TIMEOUT}, {@code CANCELED} and {@code NO_MORE_PROGRESS}, also finishes the
   * simulation, ends active steps, clears the step delay queue and, when a simulation is attached,
   * stops active injects, deletes asset agent jobs and workflow states.
   *
   * <p>{@code DELETED} is not handled yet (simulation delete/reset and scenario cleanup are
   * developed in a separate branch).
   *
   * @param workflowRun the RUN workflow reaching END/STOP
   * @param cause the reason the workflow is ending
   */
  void manageWorkflowEnd(Workflow workflowRun, WorkflowEndService.WORKFLOW_END_CAUSE cause) {
    switch (cause) {
      case TIMEOUT, CANCELED, NO_MORE_PROGRESS, CANCELED_BY_SIMULATION_DELETION ->
          endActiveWorkflow(workflowRun, cause);

      case DELETED_BY_RESET_SIMULATION -> {
        ensureWorkflowEnded(workflowRun, cause);
        deleteAttackPath(workflowRun, cause);
        workflowRepository.delete(workflowRun);
      }
      case DELETED_BY_SIMULATION_DELETION -> {
        ensureWorkflowEnded(workflowRun, cause);
        deleteAttackPath(workflowRun, cause);
        log.info(
            "[Chaining] Workflow {} {} will be deleted due to {}",
            workflowRun.getStatus(),
            workflowRun.getId(),
            cause.name());
      }
      default ->
          log.error(
              "[Chaining] Workflow {} reached END due to {}. No action defined for this cause.",
              workflowRun.getId(),
              cause.name());
    }
  }

  private void ensureWorkflowEnded(Workflow workflowRun, WORKFLOW_END_CAUSE cause) {
    if (!WorkflowStatus.END.equals(workflowRun.getStatus())) {
      log.error(
          "[Chaining] Workflow {} is not in END status when deleting it due to {}. Forcing END transition.",
          workflowRun.getId(),
          cause.name());
      endActiveWorkflow(workflowRun, WORKFLOW_END_CAUSE.CANCELED_BY_SIMULATION_DELETION);
    }
  }

  private void deleteAttackPath(Workflow workflowRun, WORKFLOW_END_CAUSE cause) {
    // Attack-path rows have no FK to the simulation, so the native exercise delete does not cascade
    // them: clear them explicitly under the caller's tenant (same primitive as the reset path),
    // otherwise a deleted simulation leaves orphan attack-path executions and findings behind.
    // Delete attack path execution
    this.attackPathExecutionIngestionService.deleteAllBySimulationId(
        workflowRun.getSimulation().getId(), workflowRun.getSimulation().getTenant().getId());
    log.info(
        "[Chaining] Attack path execution of simulation {} have been deleted due to workflow {}.",
        workflowRun.getSimulation().getId(),
        cause.name());
  }

  /**
   * Runs the END-transition cleanup (status, scope snapshot, simulation, steps, delay queue,
   * injects, asset agent jobs, workflow states) for {@code TIMEOUT}, {@code CANCELED} and {@code
   * NO_MORE_PROGRESS}. Idempotent - no-op if the workflow is already {@code END}.
   *
   * @param workflowRun the RUN workflow reaching END/STOP
   * @param cause the reason the workflow is ending
   */
  private void endActiveWorkflow(Workflow workflowRun, WORKFLOW_END_CAUSE cause) {
    if (WorkflowStatus.END.equals(workflowRun.getStatus())) {
      return;
    }
    workflowRun.setStatus(WorkflowStatus.END);
    scopeSnapshotService.freezeEnd(workflowRun, cause);

    // END SIMULATION - IF NOT MANUAL CANCEL
    stopSimulationByEndWorkflow(workflowRun, cause);
    // END ACTIVE STEP
    stepService.endActiveStepsByWorkflowId(workflowRun.getId(), cause);
    // DELETE STEP DELAY
    stepDelayQueueService.deleteAllByWorkflowRun(workflowRun, cause);

    if (workflowRun.getSimulation() == null) {
      log.error(
          "[Chaining] Workflow {} has no simulation associated. Cannot stop active injects, agent jobs and states.",
          workflowRun.getId());
      return;
    }

    // END ACTIVE INJECT
    stopActiveInjects(workflowRun.getSimulation().getId(), cause);
    // DELETE ASSET AGENT JOBS
    deleteAllAssetAgentJobsBySimulationIds(
        workflowRun.getSimulation().getId(), TenantContext.getCurrentTenant(), cause);
    // DELETE WORKFLOW STATE
    deleteWorkflowStatesBySimulationId(workflowRun.getSimulation().getId(), cause);
  }

  /**
   * Sets the workflow status to END and persists it.
   *
   * @param workflowRun the running workflow to end
   */
  public void endWorkflow(Workflow workflowRun, WorkflowEndService.WORKFLOW_END_CAUSE cause) {
    manageWorkflowEnd(workflowRun, cause);
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

  /**
   * Deletes all asset agent jobs of the given simulation, scoped to the given tenant.
   *
   * @param simulationId the ID of the simulation whose asset agent jobs should be cleared
   * @param tenantId the tenant owning the simulation, used to scope the deletion
   * @param cause the reason the workflow is ending, used for logging
   */
  protected void deleteAllAssetAgentJobsBySimulationIds(
      String simulationId, String tenantId, WORKFLOW_END_CAUSE cause) {
    int count = assetAgentJobRepository.deleteAllBySimulationIdAndTenantId(simulationId, tenantId);
    log.info(
        "[Chaining] {} asset agent jobs of simulation {} have been deleted due to {}",
        count,
        simulationId,
        cause.name());
  }

  /**
   * Deletes all workflow states associated with workflows of the given simulation.
   *
   * @param simulationId the ID of the simulation whose workflow states should be cleared
   * @param cause the reason the workflow is ending, used for logging
   */
  public void deleteWorkflowStatesBySimulationId(String simulationId, WORKFLOW_END_CAUSE cause) {
    int count = workflowStateRepository.deleteAllByWorkflowExecution_Simulation_Id(simulationId);
    log.info(
        "[Chaining] {} workflow states of simulation {} have been deleted due to {}",
        count,
        simulationId,
        cause.name());
  }
}
