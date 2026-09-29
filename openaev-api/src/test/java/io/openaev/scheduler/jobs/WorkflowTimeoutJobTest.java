package io.openaev.scheduler.jobs;

import static org.mockito.Mockito.*;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.service.chaining.WorkflowEndService;
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

  @Mock private WorkflowEndService workflowEndService;
  @Mock private TenantScopedJobRunner tenantScopedJobRunner;
  @Mock private JobExecutionContext jobExecutionContext;

  @InjectMocks private WorkflowTimeoutJob workflowTimeoutJob;

  @Nested
  @DisplayName("execute")
  class ExecuteTests {

    @Test
    @DisplayName("given_noExpiredWorkflows_should_doNothing")
    void given_noExpiredWorkflows_should_doNothing() {
      // Arrange
      when(workflowEndService.findAllExpiredRunWorkflows()).thenReturn(Collections.emptyList());

      // Act
      workflowTimeoutJob.execute(jobExecutionContext);

      // Assert
      verify(workflowEndService).findAllExpiredRunWorkflows();
      verify(workflowEndService, never()).forceCompleteWorkflowByTimeout(any());
    }

    @Test
    @DisplayName("given_singleExpiredWorkflow_should_forceCompleteIt")
    void given_singleExpiredWorkflow_should_forceCompleteIt() {
      // Arrange
      Workflow expiredWorkflow = buildRunWorkflow();
      when(workflowEndService.findAllExpiredRunWorkflows()).thenReturn(List.of(expiredWorkflow));

      // Act
      workflowTimeoutJob.execute(jobExecutionContext);

      // Assert
      verify(workflowEndService).forceCompleteWorkflowByTimeout(expiredWorkflow);
    }

    @Test
    @DisplayName("an expired run owned by a tenant is completed inside that tenant's scope")
    void given_expiredWorkflowWithATenant_should_completeItInsideThatScope() {
      // The unscoped path is already covered above, where the run has no tenant. This is the other
      // half: when the run does have one, the completion must go through the runner, which carries
      // both the v1 thread-local and the v2 GUC into forceCompleteWorkflowByTimeout.
      String tenantId = "tenant-42";
      Workflow expiredWorkflow = buildRunWorkflow();
      Exercise simulation = mock(Exercise.class);
      when(simulation.getTenant()).thenReturn(new Tenant(tenantId));
      expiredWorkflow.setSimulation(simulation);
      when(workflowEndService.findAllExpiredRunWorkflows()).thenReturn(List.of(expiredWorkflow));
      doAnswer(
              invocation -> {
                ((Runnable) invocation.getArgument(1)).run();
                return null;
              })
          .when(tenantScopedJobRunner)
          .runInTenant(eq(tenantId), any(Runnable.class));

      workflowTimeoutJob.execute(jobExecutionContext);

      verify(tenantScopedJobRunner).runInTenant(eq(tenantId), any(Runnable.class));
      verify(workflowEndService).forceCompleteWorkflowByTimeout(expiredWorkflow);
    }

    @Test
    @DisplayName("given_multipleExpiredWorkflows_should_forceCompleteAll")
    void given_multipleExpiredWorkflows_should_forceCompleteAll() {
      // Arrange
      Workflow expired1 = buildRunWorkflow();
      Workflow expired2 = buildRunWorkflow();
      when(workflowEndService.findAllExpiredRunWorkflows()).thenReturn(List.of(expired1, expired2));

      // Act
      workflowTimeoutJob.execute(jobExecutionContext);

      // Assert
      verify(workflowEndService).forceCompleteWorkflowByTimeout(expired1);
      verify(workflowEndService).forceCompleteWorkflowByTimeout(expired2);
    }

    @Test
    @DisplayName(
        "given_firstWorkflowFailsForceComplete_should_continueProcessingRemainingWorkflows")
    void given_firstWorkflowFailsForceComplete_should_continueProcessingRemainingWorkflows() {
      // Arrange
      Workflow failingWorkflow = buildRunWorkflow();
      Workflow successWorkflow = buildRunWorkflow();
      when(workflowEndService.findAllExpiredRunWorkflows())
          .thenReturn(List.of(failingWorkflow, successWorkflow));
      doThrow(new RuntimeException("DB error"))
          .when(workflowEndService)
          .forceCompleteWorkflowByTimeout(failingWorkflow);

      // Act
      workflowTimeoutJob.execute(jobExecutionContext);

      // Assert
      verify(workflowEndService).forceCompleteWorkflowByTimeout(failingWorkflow);
      verify(workflowEndService).forceCompleteWorkflowByTimeout(successWorkflow);
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
