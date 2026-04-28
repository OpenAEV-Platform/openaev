package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.StepRepository;
import java.util.Optional;
import java.util.UUID;
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
  @Mock private InjectExecutionStep injectExecutionStep;
  @Mock private WorkflowService workflowService;
  @Mock private ConditionService conditionService;
  @Mock private QueueChainingService queueChainingService;
  @Mock private StepDelayQueueRepository stepDelayQueueRepository;

  @Spy @InjectMocks private StepService stepService;

  // ========================================================================
  // ready() — guard on ended workflow
  // ========================================================================
  @Nested
  @DisplayName("ready — timeout guard")
  class ReadyGuardTests {

    @Test
    @DisplayName("given_workflowEnded_should_returnEmptyAndSkipExecution")
    void given_workflowEnded_should_returnEmptyAndSkipExecution() throws Exception {
      // Arrange
      Workflow endedWorkflow = buildWorkflow(WorkflowStatus.END);
      Step stepTemplate = buildStep(StepStatus.TEMPLATE, endedWorkflow);

      // Act
      Optional<Step> result = stepService.ready(stepTemplate, endedWorkflow, null);

      // Assert
      assertTrue(result.isEmpty());
      verify(conditionService, never()).checkCondition(any(), any(), any(), any());
      verify(queueChainingService, never()).readyStep(any(), any());
    }

    @Test
    @DisplayName("given_workflowRunning_should_proceedNormally")
    void given_workflowRunning_should_proceedNormally() throws Exception {
      // Arrange
      Workflow runningWorkflow = buildWorkflow(WorkflowStatus.RUN);
      Step stepTemplate = buildStep(StepStatus.TEMPLATE, runningWorkflow);

      when(stepRepository.findByIdAndStatus(stepTemplate.getId(), StepStatus.TEMPLATE))
          .thenReturn(Optional.of(stepTemplate));
      when(conditionService.checkCondition(any(), any(), any(), any())).thenReturn(null);

      // Act
      Optional<Step> result = stepService.ready(stepTemplate, runningWorkflow, null);

      // Assert — returns empty because conditions returned null (no execution), but it DID proceed
      assertTrue(result.isEmpty());
      verify(conditionService).checkCondition(any(), any(), any(), any());
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
    void given_workflowEnded_should_skipRunAndNotExecute() {
      // Arrange
      Workflow endedWorkflow = buildWorkflow(WorkflowStatus.END);
      Step stepReady = buildStep(StepStatus.READY, endedWorkflow);
      when(workflowService.isWorkflowEnded(endedWorkflow.getId())).thenReturn(true);

      // Act
      stepService.run(stepReady);

      // Assert — no action step executed, no status change saved
      verify(stepRepository, never()).save(any());
    }

    @Test
    @DisplayName("given_workflowRunning_should_proceedWithExecution")
    void given_workflowRunning_should_proceedWithExecution() throws Exception {
      // Arrange
      Workflow runningWorkflow = buildWorkflow(WorkflowStatus.RUN);
      Step stepReady = buildStep(StepStatus.READY, runningWorkflow);
      stepReady.setStepAction(StepActionClass.INJECT_EXECUTION);
      when(workflowService.isWorkflowEnded(runningWorkflow.getId())).thenReturn(false);

      Step stepRun = buildStep(StepStatus.RUN, runningWorkflow);
      when(injectExecutionStep.run(stepReady)).thenReturn(Optional.of(stepRun));
      when(stepRepository.save(any())).thenReturn(stepRun);

      // Act
      stepService.run(stepReady);

      // Assert — step was saved with RUN status
      verify(stepRepository).save(any());
    }

    @Test
    @DisplayName("given_nullWorkflow_should_proceedWithExecution")
    void given_nullWorkflow_should_proceedWithExecution() throws Exception {
      // Arrange
      Step stepReady = buildStep(StepStatus.READY, null);
      stepReady.setStepAction(StepActionClass.INJECT_EXECUTION);

      Step stepRun = new Step();
      stepRun.setId(UUID.randomUUID().toString());
      stepRun.setStatus(StepStatus.RUN);
      when(injectExecutionStep.run(stepReady)).thenReturn(Optional.of(stepRun));
      when(stepRepository.save(any())).thenReturn(stepRun);

      // Act
      stepService.run(stepReady);

      // Assert — guard did not block, execution proceeded
      verify(stepRepository).save(any());
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
    void given_stepRunWithEndedWorkflow_should_ignoreEvent() {
      // Arrange
      Workflow endedWorkflow = buildWorkflow(WorkflowStatus.END);
      Step stepRun = buildStep(StepStatus.RUN, endedWorkflow);

      ExternalUpdateEvent event = ExternalUpdateEvent.builder().stepId(stepRun.getId()).build();

      when(stepRepository.findByIdAndStatus(stepRun.getId(), StepStatus.RUN))
          .thenReturn(Optional.of(stepRun));
      when(workflowService.isWorkflowEnded(endedWorkflow.getId())).thenReturn(true);

      // Act
      stepService.handleExternalUpdateEvent(event);

      // Assert — no update attempted, no next steps triggered
      verify(stepRepository, never()).save(any());
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

      when(stepRepository.findByIdAndStatus(stepRun.getId(), StepStatus.RUN))
          .thenReturn(Optional.of(stepRun));
      when(workflowService.isWorkflowEnded(runningWorkflow.getId())).thenReturn(false);
      // update returns empty → no next steps, but the guard was passed
      when(injectExecutionStep.update(stepRun)).thenReturn(Optional.empty());

      // Act
      stepService.handleExternalUpdateEvent(event);

      // Assert — update was attempted (guard passed)
      verify(injectExecutionStep).update(stepRun);
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
