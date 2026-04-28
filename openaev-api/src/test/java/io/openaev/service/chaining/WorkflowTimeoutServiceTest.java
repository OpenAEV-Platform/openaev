package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowTimeoutService Tests")
class WorkflowTimeoutServiceTest {

  @Mock private WorkflowService workflowService;
  @Mock private StepService stepService;
  @Mock private StepDelayQueueService stepDelayQueueService;

  @InjectMocks private WorkflowTimeoutService workflowTimeoutService;

  @Nested
  @DisplayName("forceCompleteWorkflow")
  class ForceCompleteWorkflowTests {

    @Test
    @DisplayName("should_endSteps_deleteDelayQueue_andEndWorkflow_inOrder")
    void should_endSteps_deleteDelayQueue_andEndWorkflow_inOrder() {
      // Arrange
      Workflow workflowRun = buildRunWorkflow();
      when(stepService.endActiveStepsByWorkflowId(workflowRun.getId())).thenReturn(3);

      // Act
      workflowTimeoutService.forceCompleteWorkflow(workflowRun);

      // Assert — verify ordering: steps first, delay queue second, workflow last
      InOrder inOrder = inOrder(stepService, stepDelayQueueService, workflowService);
      inOrder.verify(stepService).endActiveStepsByWorkflowId(workflowRun.getId());
      inOrder.verify(stepDelayQueueService).deleteAllByWorkflowRun(workflowRun);
      inOrder.verify(workflowService).endWorkflow(workflowRun);
    }

    @Test
    @DisplayName("given_noActiveSteps_should_stillEndWorkflowAndPurgeDelayQueue")
    void given_noActiveSteps_should_stillEndWorkflowAndPurgeDelayQueue() {
      // Arrange
      Workflow workflowRun = buildRunWorkflow();
      when(stepService.endActiveStepsByWorkflowId(workflowRun.getId())).thenReturn(0);

      // Act
      workflowTimeoutService.forceCompleteWorkflow(workflowRun);

      // Assert — all operations still called even with 0 active steps
      verify(stepService).endActiveStepsByWorkflowId(workflowRun.getId());
      verify(stepDelayQueueService).deleteAllByWorkflowRun(workflowRun);
      verify(workflowService).endWorkflow(workflowRun);
    }
  }

  @Nested
  @DisplayName("findAllExpiredRunWorkflows")
  class FindAllExpiredRunWorkflowsTests {

    @Test
    @DisplayName("should_delegateToWorkflowService")
    void should_delegateToWorkflowService() {
      // Arrange
      List<Workflow> expected = List.of(buildRunWorkflow());
      when(workflowService.findAllExpiredRunWorkflows()).thenReturn(expected);

      // Act
      List<Workflow> result = workflowTimeoutService.findAllExpiredRunWorkflows();

      // Assert
      assertEquals(expected, result);
      verify(workflowService).findAllExpiredRunWorkflows();
    }
  }

  private static Workflow buildRunWorkflow() {
    Workflow workflow = new Workflow();
    workflow.setId(UUID.randomUUID().toString());
    workflow.setStatus(WorkflowStatus.RUN);
    return workflow;
  }
}
