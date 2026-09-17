package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.rest.exception.ChainingException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowPauseServiceTest {

  @Mock private WorkflowService workflowService;
  @Mock private StepDelayQueueService stepDelayQueueService;
  @Mock private StepService stepService;

  @Test
  void givenRunningWorkflow_whenPause_shouldStopWorkflowAndNullifyDelayGoals() {
    // Arrange
    Workflow run = new Workflow();
    run.setId("run-1");
    run.setStatus(WorkflowStatus.RUN);
    when(workflowService.findWorkflowRunBySimulationId("sim-1")).thenReturn(List.of(run));

    WorkflowPauseService service =
        new WorkflowPauseService(workflowService, stepDelayQueueService, stepService);

    // Act
    service.pauseSimulationWorkflowRuns("sim-1");

    // Assert
    assertEquals(WorkflowStatus.STOP, run.getStatus());
    assertNotNull(run.getPauseAt());
    verify(stepDelayQueueService).nullifyGoalsByWorkflowRun(run);
    verify(workflowService).saveWorkflowRun(run);
  }

  @Test
  void givenStoppedWorkflow_whenResume_shouldAccumulatePauseAndRequeueReadySteps()
      throws ChainingException {
    // Arrange
    Workflow stoppedRun = new Workflow();
    stoppedRun.setId("run-1");
    stoppedRun.setStatus(WorkflowStatus.STOP);
    stoppedRun.setPauseAt(Instant.now().minusSeconds(120));
    stoppedRun.setPauseSecond(10L);
    Step readyStep = new Step();
    readyStep.setStatus(StepStatus.READY);
    readyStep.setWorkflow(stoppedRun);

    when(workflowService.findWorkflowStoppedBySimulationId("sim-1"))
        .thenReturn(List.of(stoppedRun));
    when(stepService.findAllStepsByWorkflowRunIdAndStatus("run-1", StepStatus.READY))
        .thenReturn(List.of(readyStep));

    WorkflowPauseService service =
        new WorkflowPauseService(workflowService, stepDelayQueueService, stepService);

    // Act
    service.resumeSimulationWorkflowRuns("sim-1");

    // Assert
    assertEquals(WorkflowStatus.RUN, stoppedRun.getStatus());
    assertNull(stoppedRun.getPauseAt());
    assertTrue(stoppedRun.getPauseSecond() >= 130L);
    verify(stepDelayQueueService).recalculateGoalsOnResume(eq(stoppedRun), any(), any());
    verify(stepService).enqueueReadySteps(List.of(readyStep), stoppedRun);
    verify(workflowService).saveWorkflowRun(stoppedRun);
  }
}
