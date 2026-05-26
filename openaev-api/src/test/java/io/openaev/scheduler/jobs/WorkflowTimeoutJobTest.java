package io.openaev.scheduler.jobs;

import static org.mockito.Mockito.*;

import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.service.chaining.WorkflowTimeoutService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowTimeoutJob Tests")
class WorkflowTimeoutJobTest {

  @Mock private WorkflowTimeoutService workflowTimeoutService;
  @Mock private JobExecutionContext jobExecutionContext;

  @InjectMocks private WorkflowTimeoutJob workflowTimeoutJob;

  @Nested
  @DisplayName("execute")
  class ExecuteTests {

    @Test
    @DisplayName("given_noExpiredWorkflows_should_doNothing")
    void given_noExpiredWorkflows_should_doNothing() {
      // Arrange
      when(workflowTimeoutService.findAllExpiredRunWorkflows()).thenReturn(Collections.emptyList());

      // Act
      workflowTimeoutJob.execute(jobExecutionContext);

      // Assert
      verify(workflowTimeoutService).findAllExpiredRunWorkflows();
      verify(workflowTimeoutService, never()).forceCompleteWorkflow(any());
    }

    @Test
    @DisplayName("given_singleExpiredWorkflow_should_forceCompleteIt")
    void given_singleExpiredWorkflow_should_forceCompleteIt() {
      // Arrange
      Workflow expiredWorkflow = buildRunWorkflow();
      when(workflowTimeoutService.findAllExpiredRunWorkflows())
          .thenReturn(List.of(expiredWorkflow));

      // Act
      workflowTimeoutJob.execute(jobExecutionContext);

      // Assert
      verify(workflowTimeoutService).forceCompleteWorkflow(expiredWorkflow);
    }

    @Test
    @DisplayName("given_multipleExpiredWorkflows_should_forceCompleteAll")
    void given_multipleExpiredWorkflows_should_forceCompleteAll() {
      // Arrange
      Workflow expired1 = buildRunWorkflow();
      Workflow expired2 = buildRunWorkflow();
      when(workflowTimeoutService.findAllExpiredRunWorkflows())
          .thenReturn(List.of(expired1, expired2));

      // Act
      workflowTimeoutJob.execute(jobExecutionContext);

      // Assert
      verify(workflowTimeoutService).forceCompleteWorkflow(expired1);
      verify(workflowTimeoutService).forceCompleteWorkflow(expired2);
    }

    @Test
    @DisplayName(
        "given_firstWorkflowFailsForceComplete_should_continueProcessingRemainingWorkflows")
    void given_firstWorkflowFailsForceComplete_should_continueProcessingRemainingWorkflows() {
      // Arrange
      Workflow failingWorkflow = buildRunWorkflow();
      Workflow successWorkflow = buildRunWorkflow();
      when(workflowTimeoutService.findAllExpiredRunWorkflows())
          .thenReturn(List.of(failingWorkflow, successWorkflow));
      doThrow(new RuntimeException("DB error"))
          .when(workflowTimeoutService)
          .forceCompleteWorkflow(failingWorkflow);

      // Act
      workflowTimeoutJob.execute(jobExecutionContext);

      // Assert
      verify(workflowTimeoutService).forceCompleteWorkflow(failingWorkflow);
      verify(workflowTimeoutService).forceCompleteWorkflow(successWorkflow);
    }
  }

  private static Workflow buildRunWorkflow() {
    Workflow workflow = new Workflow();
    workflow.setId(java.util.UUID.randomUUID().toString());
    workflow.setStatus(WorkflowStatus.RUN);
    workflow.setTimeoutEnabled(true);
    workflow.setTimeoutSeconds(60L);
    return workflow;
  }
}
