package io.openaev.service.chaining;

import static org.mockito.Mockito.*;

import io.openaev.api.chaining.ActionStep;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StepService Timeout Guards Tests")
class StepServiceTimeoutGuardsTest {

  @Mock private StepRepository stepRepository;
  @Mock private ChainingConfig chainingConfig;
  @Mock private StepService stepService;
  @Mock private WorkflowService workflowService;
  @Mock private QueueChainingService queueChainingService;
  @Mock private TenantScopedTransaction tenantTx;
  @Mock private ActionStep actionStep;

  @Spy @InjectMocks private StepEventService stepEventService;

  @BeforeEach
  void setUp() {
    lenient()
        .doAnswer(
            invocation -> {
              Runnable work = invocation.getArgument(1);
              work.run();
              return null;
            })
        .when(tenantTx)
        .execute(any(TxCtx.class), any(Runnable.class));
  }

  // ========================================================================
  // handleReadyStepEvent() / run() — guard on ended workflow
  // ========================================================================
  @Nested
  @DisplayName("ready event / run — timeout guard")
  class ReadyGuardTests {

    @Test
    @DisplayName("given_workflowEnded_should_skipExecution")
    void given_workflowEnded_should_skipExecution() throws Exception {
      // Arrange
      Workflow endedWorkflow = buildWorkflow(WorkflowStatus.END);
      Step stepReady = buildStep(StepStatus.READY, endedWorkflow);
      String stepId = stepReady.getId();

      StepEvent event = StepEvent.builder().stepId(stepId).build();
      when(stepRepository.findById(stepId)).thenReturn(Optional.of(stepReady));
      when(workflowService.isWorkflowEnded(endedWorkflow.getId())).thenReturn(true);

      // Act
      stepEventService.handleReadyStepEvent(event);

      // Assert
      verify(stepService, never()).factoryAction(any(), any());
      verify(stepService, never()).saveStep(any());
    }

    @Test
    @DisplayName("given_workflowRunning_should_proceedWithExecution")
    void given_workflowRunning_should_proceedWithExecution() throws Exception {
      // Arrange
      Workflow runningWorkflow = buildWorkflow(WorkflowStatus.RUN);
      Step stepReady = buildStep(StepStatus.READY, runningWorkflow);
      Step stepRun = buildStep(StepStatus.RUN, runningWorkflow);
      String stepId = stepReady.getId();

      StepEvent event = StepEvent.builder().stepId(stepId).build();
      when(stepRepository.findById(stepId)).thenReturn(Optional.of(stepReady));
      when(workflowService.isWorkflowEnded(runningWorkflow.getId())).thenReturn(false);
      when(stepService.factoryAction(stepReady.getStepAction(), stepId)).thenReturn(actionStep);
      when(actionStep.run(stepReady)).thenReturn(Optional.of(stepRun));

      // Act
      stepEventService.handleReadyStepEvent(event);

      // Assert
      verify(actionStep).run(stepReady);
      verify(stepService).saveStep(stepRun);
    }
  }

  // ========================================================================
  // run() — guard on ended workflow
  // ========================================================================
  @Nested
  @DisplayName("run — timeout guard")
  class RunGuardTests {

    @Test
    @DisplayName("given_workflowEnded_should_skipRunAndNotExecute")
    void given_workflowEnded_should_skipRunAndNotExecute() throws Exception {
      // Arrange
      Workflow endedWorkflow = buildWorkflow(WorkflowStatus.END);
      Step stepReady = buildStep(StepStatus.READY, endedWorkflow);
      when(workflowService.isWorkflowEnded(endedWorkflow.getId())).thenReturn(true);

      // Act
      stepEventService.run(stepReady);

      // Assert — no action step executed, no status change saved
      verify(stepService, never()).factoryAction(any(), any());
      verify(stepService, never()).saveStep(any());
    }

    @Test
    @DisplayName("given_workflowRunning_should_proceedWithExecution")
    void given_workflowRunning_should_proceedWithExecution() throws Exception {
      // Arrange
      Workflow runningWorkflow = buildWorkflow(WorkflowStatus.RUN);
      Step stepReady = buildStep(StepStatus.READY, runningWorkflow);
      String stepReadyId = stepReady.getId();
      when(workflowService.isWorkflowEnded(runningWorkflow.getId())).thenReturn(false);

      Step stepRun = buildStep(StepStatus.RUN, runningWorkflow);
      when(stepService.factoryAction(stepReady.getStepAction(), stepReadyId))
          .thenReturn(actionStep);
      when(actionStep.run(stepReady)).thenReturn(Optional.of(stepRun));
      when(stepService.saveStep(stepRun)).thenReturn(stepRun);

      // Act
      stepEventService.run(stepReady);

      // Assert — step was saved with RUN status
      verify(actionStep).run(stepReady);
      verify(stepService).saveStep(stepRun);
    }

    @Test
    @DisplayName("given_nullWorkflow_should_proceedWithExecution")
    void given_nullWorkflow_should_proceedWithExecution() throws Exception {
      // Arrange
      Step stepReady = buildStep(StepStatus.READY, null);
      String stepReadyId = stepReady.getId();

      Step stepRun = new Step();
      stepRun.setId(UUID.randomUUID().toString());
      stepRun.setStatus(StepStatus.RUN);
      when(stepService.factoryAction(stepReady.getStepAction(), stepReadyId))
          .thenReturn(actionStep);
      when(actionStep.run(stepReady)).thenReturn(Optional.of(stepRun));
      when(stepService.saveStep(stepRun)).thenReturn(stepRun);

      // Act
      stepEventService.run(stepReady);

      // Assert — guard did not block, execution proceeded
      verify(actionStep).run(stepReady);
      verify(stepService).saveStep(stepRun);
      verify(workflowService, never()).isWorkflowEnded(any());
    }
  }

  // ========================================================================
  // handleExternalUpdateEvent() — guard on ended workflow
  // ========================================================================
  @Nested
  @DisplayName("handleExternalUpdateEvent — timeout guard")
  class HandleExternalUpdateEventGuardTests {

    @Test
    @DisplayName("given_stepRunWithEndedWorkflow_should_ignoreEvent")
    void given_stepRunWithEndedWorkflow_should_ignoreEvent() throws Exception {
      // Arrange
      Workflow endedWorkflow = buildWorkflow(WorkflowStatus.END);
      Step stepRun = buildStep(StepStatus.RUN, endedWorkflow);

      ExternalUpdateEvent event = ExternalUpdateEvent.builder().stepId(stepRun.getId()).build();

      when(stepService.findByIdAndStatus(stepRun.getId(), StepStatus.RUN)).thenReturn(stepRun);
      when(workflowService.isWorkflowEnded(endedWorkflow.getId())).thenReturn(true);

      // Act
      stepEventService.handleExternalUpdateEvent(event);

      // Assert — no update attempted, no next steps triggered
      verify(stepService, never()).factoryAction(any(), any());
      verify(stepService, never()).saveStep(any());
    }

    @Test
    @DisplayName("given_stepRunWithRunningWorkflow_should_proceedWithUpdate")
    void given_stepRunWithRunningWorkflow_should_proceedWithUpdate() throws Exception {
      // Arrange
      Workflow runningWorkflow = buildWorkflow(WorkflowStatus.RUN);
      Step stepTemplate = buildStep(StepStatus.TEMPLATE, runningWorkflow);
      Step stepRun = buildStep(StepStatus.RUN, runningWorkflow);
      stepRun.setStepTemplate(stepTemplate);
      stepRun.setStepAction(StepActionClass.INJECT_EXECUTION);

      ExternalUpdateEvent event = ExternalUpdateEvent.builder().stepId(stepRun.getId()).build();

      when(stepService.findByIdAndStatus(stepRun.getId(), StepStatus.RUN)).thenReturn(stepRun);
      when(workflowService.isWorkflowEnded(runningWorkflow.getId())).thenReturn(false);
      when(stepService.factoryAction(stepRun.getStepAction(), stepRun.getId()))
          .thenReturn(actionStep);
      // update returns empty → no next steps, but the guard was passed
      when(actionStep.update(stepRun)).thenReturn(Optional.empty());

      // Act
      stepEventService.handleExternalUpdateEvent(event);

      // Assert — update was attempted (guard passed)
      verify(actionStep).update(stepRun);
      verify(stepService, never()).saveStep(any());
    }
  }

  // ========================================================================
  // Helpers
  // ========================================================================

  private static Workflow buildWorkflow(WorkflowStatus status) {
    Workflow workflow = new Workflow();
    workflow.setId(UUID.randomUUID().toString());
    workflow.setStatus(status);
    return workflow;
  }

  private static Step buildStep(StepStatus status, Workflow workflow) {
    Step step = new Step();
    step.setId(UUID.randomUUID().toString());
    step.setStatus(status);
    step.setStepAction(StepActionClass.INJECT_EXECUTION);
    step.setWorkflow(workflow);
    return step;
  }
}
