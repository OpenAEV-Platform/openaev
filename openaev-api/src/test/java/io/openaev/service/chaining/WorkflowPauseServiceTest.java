package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.rest.exception.ChainingException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowPauseServiceTest {

  @Mock private WorkflowService workflowService;
  @Mock private WorkflowResumeService workflowResumeService;
  @Mock private StepDelayQueueService stepDelayQueueService;
  @Mock private StepService stepService;
  @Mock private EntityManager entityManager;

  @Test
  void givenRunningWorkflow_whenPause_shouldStopWorkflowAndNullifyDelayGoals() {
    // Arrange
    Workflow run = new Workflow();
    run.setId("run-1");
    run.setStatus(WorkflowStatus.RUN);
    when(workflowService.findCurrentWorkflowExecutionBySimulationId("sim-1"))
        .thenReturn(Optional.of(run));

    WorkflowPauseService service =
        new WorkflowPauseService(
            workflowService,
            workflowResumeService,
            stepDelayQueueService,
            stepService,
            entityManager);

    // Act
    service.pauseSimulationWorkflowRuns("sim-1");

    // Assert
    assertEquals(WorkflowStatus.STOP, run.getStatus());
    assertNotNull(run.getPauseAt());
    verify(stepDelayQueueService).nullifyGoalsByWorkflowRun(run);
    verify(workflowService).saveWorkflowRun(run);
  }

  @Test
  void givenCurrentExecutionNotRunning_whenPause_shouldLeaveItUntouched() {
    // Arrange: the latest execution has ended; an older RUN row of a legacy database must not be
    // picked up instead.
    Workflow endedRun = new Workflow();
    endedRun.setId("run-2");
    endedRun.setStatus(WorkflowStatus.END);
    when(workflowService.findCurrentWorkflowExecutionBySimulationId("sim-1"))
        .thenReturn(Optional.of(endedRun));

    WorkflowPauseService service =
        new WorkflowPauseService(
            workflowService,
            workflowResumeService,
            stepDelayQueueService,
            stepService,
            entityManager);

    // Act
    service.pauseSimulationWorkflowRuns("sim-1");

    // Assert
    assertEquals(WorkflowStatus.END, endedRun.getStatus());
    verify(stepDelayQueueService, never()).nullifyGoalsByWorkflowRun(any());
    verify(workflowService, never()).saveWorkflowRun(any());
  }

  @Test
  void givenStoppedWorkflow_whenResume_shouldCommitRunBeforeRequeueingReadySteps()
      throws ChainingException {
    // Arrange
    Workflow stoppedRun = stoppedRunOfTenant("tenant-1");
    Step readyStep = new Step();
    readyStep.setStatus(StepStatus.READY);
    readyStep.setWorkflow(stoppedRun);

    when(workflowService.findCurrentWorkflowExecutionBySimulationId("sim-1"))
        .thenReturn(Optional.of(stoppedRun));
    when(workflowResumeService.resumeWorkflowRunIsolated(any(TxCtx.class), eq("run-1"), any()))
        .thenReturn(true);
    when(workflowService.evaluateWorkflowProgress(stoppedRun)).thenReturn(stoppedRun);
    when(stepService.findAllStepsByWorkflowRunIdAndStatus("run-1", StepStatus.READY))
        .thenReturn(List.of(readyStep));

    WorkflowPauseService service =
        new WorkflowPauseService(
            workflowService,
            workflowResumeService,
            stepDelayQueueService,
            stepService,
            entityManager);

    // Act
    service.resumeSimulationWorkflowRuns("sim-1");

    // Assert: RUN committed first, then the stale run reloaded, then READY published and
    // progress re-evaluated even though READY steps existed.
    InOrder inOrder = inOrder(workflowResumeService, entityManager, stepService, workflowService);
    inOrder
        .verify(workflowResumeService)
        .resumeWorkflowRunIsolated(any(TxCtx.class), eq("run-1"), any());
    inOrder.verify(entityManager).refresh(stoppedRun);
    inOrder.verify(stepService).enqueueReadySteps(List.of(readyStep), stoppedRun);
    inOrder.verify(workflowService).evaluateWorkflowProgress(stoppedRun);
  }

  @Test
  void givenRunNoLongerStopped_whenResume_shouldPublishNothing() throws ChainingException {
    // Arrange: another resume won the race between the lookup and the isolated commit.
    Workflow stoppedRun = stoppedRunOfTenant("tenant-1");
    when(workflowService.findCurrentWorkflowExecutionBySimulationId("sim-1"))
        .thenReturn(Optional.of(stoppedRun));
    when(workflowResumeService.resumeWorkflowRunIsolated(any(TxCtx.class), eq("run-1"), any()))
        .thenReturn(false);

    WorkflowPauseService service =
        new WorkflowPauseService(
            workflowService,
            workflowResumeService,
            stepDelayQueueService,
            stepService,
            entityManager);

    // Act
    boolean ended = service.resumeSimulationWorkflowRuns("sim-1");

    // Assert
    assertFalse(ended);
    verifyNoInteractions(stepService, entityManager);
    verify(workflowService, never()).evaluateWorkflowProgress(any());
  }

  private static Workflow stoppedRunOfTenant(String tenantId) {
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    Exercise simulation = new Exercise();
    simulation.setTenant(tenant);
    Workflow stoppedRun = new Workflow();
    stoppedRun.setId("run-1");
    stoppedRun.setStatus(WorkflowStatus.STOP);
    stoppedRun.setPauseAt(Instant.now().minusSeconds(120));
    stoppedRun.setSimulation(simulation);
    return stoppedRun;
  }
}
