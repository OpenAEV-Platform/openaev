package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.ActionStep;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import java.util.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StepEventServiceTest {

  @Mock private StepService stepService;
  @Mock private WorkflowService workflowService;
  @Mock private StepRepository stepRepository;
  @Mock private ActionStep actionStep;

  @InjectMocks private StepEventService stepEventService;

  // -- RUN --

  @Nested
  class Run {

    @Test
    void shouldMoveStepToEndWhenActionStepIsNull() throws ChainingException {
      // -------- Prepare --------
      Step stepReady = mock(Step.class);

      when(stepService.factoryAction(stepReady.getStepAction(), stepReady.getId()))
          .thenThrow(new ChainingException("Action step is null"));

      // -------- Act --------
      stepEventService.run(stepReady);

      // -------- Assert --------
      verify(stepReady).setStatus(StepStatus.END);
      verify(stepService).saveStep(stepReady);
    }

    @Test
    void shouldEndStepOnly_whenStepReadyExecutionFailed() throws ChainingException {
      // -------- Prepare --------
      Step stepReady = mock(Step.class);
      ActionStep actionStep = mock(ActionStep.class);

      when(stepReady.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
      when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, null))
          .thenReturn(actionStep);
      when(actionStep.run(stepReady)).thenReturn(Optional.empty());

      // -------- Act --------
      stepEventService.run(stepReady);

      // -------- Assert --------
      verify(stepReady).setStatus(StepStatus.END);
      verify(stepService).saveStep(stepReady);
    }

    @Test
    void shouldSetRunStatusAndSaveStep_whenRunReturnsStep() throws ChainingException {
      // -------- Prepare --------
      Step stepReady = mock(Step.class);
      Step stepRun = mock(Step.class);
      ActionStep actionStep = mock(ActionStep.class);

      when(stepReady.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
      when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, null))
          .thenReturn(actionStep);
      when(actionStep.run(stepReady)).thenReturn(Optional.of(stepRun));

      // -------- Act --------
      stepEventService.run(stepReady);

      // -------- Assert --------
      verify(stepRun).setStatus(StepStatus.RUN);
      verify(stepService).saveStep(stepRun);
    }

    @Test
    void shouldRunStepSuccessfully() throws ChainingException {
      // -------- Prepare --------
      Step stepReady = new Step();
      stepReady.setStepAction(StepActionClass.INJECT_EXECUTION);
      Step stepRun = new Step();

      when(stepService.factoryAction(eq(StepActionClass.INJECT_EXECUTION), any()))
          .thenReturn(actionStep);
      when(actionStep.run(stepReady)).thenReturn(Optional.of(stepRun));
      when(stepService.saveStep(stepRun)).thenReturn(stepRun);

      // -------- Act --------
      stepEventService.run(stepReady);

      // -------- Assert --------
      assertEquals(StepStatus.RUN, stepRun.getStatus());
      verify(stepService).saveStep(stepRun);
    }

    @Test
    void shouldSetStepReadyToEndWhenRunReturnsEmpty() throws ChainingException {
      // -------- Prepare --------
      Step stepReady = new Step();
      stepReady.setStepAction(StepActionClass.INJECT_EXECUTION);

      when(stepService.factoryAction(any(), any())).thenReturn(actionStep);
      when(actionStep.run(stepReady)).thenReturn(Optional.empty());
      when(stepService.saveStep(stepReady)).thenReturn(stepReady);

      // -------- Act --------
      stepEventService.run(stepReady);

      // -------- Assert --------
      assertEquals(StepStatus.END, stepReady.getStatus());
      verify(stepService).saveStep(stepReady);
    }
  }

  // -- BATCH HANDLERS --

  @Nested
  class BatchHandlers {

    @Test
    void given_readyEvents_should_consumeAndReturnSameList() {
      // Arrange
      StepEvent e1 = mock(StepEvent.class);
      StepEvent e2 = mock(StepEvent.class);
      List<StepEvent> events = List.of(e1, e2);

      when(e1.getStepId()).thenReturn(UUID.randomUUID().toString());
      when(e2.getStepId()).thenReturn(UUID.randomUUID().toString());

      // Act
      List<StepEvent> result = stepEventService.handleReadyEvent(events);

      // Assert
      assertSame(events, result);
    }

    @Test
    void given_externalUpdateEvents_should_consumeAndReturnSameList() {
      // Arrange
      ExternalUpdateEvent e1 = mock(ExternalUpdateEvent.class);
      ExternalUpdateEvent e2 = mock(ExternalUpdateEvent.class);
      List<ExternalUpdateEvent> events = List.of(e1, e2);

      String stepRunId1 = UUID.randomUUID().toString();
      String stepRunId2 = UUID.randomUUID().toString();
      when(e1.getStepId()).thenReturn(stepRunId1);
      when(e2.getStepId()).thenReturn(stepRunId2);

      // Both steps not found — early return per event, no crash
      when(stepService.findByIdAndStatus(stepRunId1, StepStatus.RUN))
          .thenThrow(new ElementNotFoundException("not found"));
      when(stepService.findByIdAndStatus(stepRunId2, StepStatus.RUN))
          .thenThrow(new ElementNotFoundException("not found"));

      // Act
      List<ExternalUpdateEvent> result = stepEventService.handleExternalUpdateEvent(events);

      // Assert
      assertSame(events, result);
    }
  }

  // -- HANDLE READY STEP EVENT --

  @Nested
  class HandleReadyStepEvent {

    @Test
    void given_existingStep_should_runIt() throws ChainingException {
      // Arrange
      StepEvent event = mock(StepEvent.class);
      String stepId = UUID.randomUUID().toString();
      when(event.getStepId()).thenReturn(stepId);

      Step step = new Step();
      step.setStepAction(StepActionClass.INJECT_EXECUTION);
      Step stepRun = new Step();

      when(stepRepository.findById(stepId)).thenReturn(Optional.of(step));
      when(stepService.factoryAction(eq(StepActionClass.INJECT_EXECUTION), any()))
          .thenReturn(actionStep);
      when(actionStep.run(step)).thenReturn(Optional.of(stepRun));

      // Act
      stepEventService.handleReadyStepEvent(event);

      // Assert
      verify(stepRepository).findById(stepId);
      assertEquals(StepStatus.RUN, stepRun.getStatus());
      verify(stepService).saveStep(stepRun);
    }

    @Test
    void given_missingStep_should_notRun() {
      // Arrange
      StepEvent event = mock(StepEvent.class);
      String stepId = UUID.randomUUID().toString();
      when(event.getStepId()).thenReturn(stepId);

      when(stepRepository.findById(stepId)).thenReturn(Optional.empty());

      // Act
      stepEventService.handleReadyStepEvent(event);

      // Assert
      verify(stepRepository).findById(stepId);
      verify(stepService, never()).saveStep(any());
    }
  }

  // -- HANDLE EXTERNAL UPDATE EVENT --

  @Nested
  class HandleExternalUpdateEventSingle {

    @Test
    void shouldEndStepWhenActionStepIsNull() throws ChainingException {
      // -------- Prepare --------
      ExternalUpdateEvent event = mock(ExternalUpdateEvent.class);
      String stepRunId = UUID.randomUUID().toString();
      when(event.getStepId()).thenReturn(stepRunId);

      Step stepRun = mock(Step.class);
      when(stepRun.getStepAction()).thenReturn(null);

      when(stepService.findByIdAndStatus(stepRunId, StepStatus.RUN)).thenReturn(stepRun);

      when(stepService.factoryAction(null, null))
          .thenThrow(new ChainingException("Action step is null"));

      // -------- Act --------
      stepEventService.handleExternalUpdateEvent(event);

      // -------- Assert --------
      verify(stepRun).setStatus(StepStatus.END);
      verify(stepService).saveStep(stepRun);
    }

    @Test
    void shouldDoNothing_whenStepRunNotFound() {
      // -------- Prepare --------
      ExternalUpdateEvent event = mock(ExternalUpdateEvent.class);
      String stepRunId = UUID.randomUUID().toString();
      when(event.getStepId()).thenReturn(stepRunId);

      when(stepService.findByIdAndStatus(stepRunId, StepStatus.RUN))
          .thenThrow(new ElementNotFoundException("not found"));

      // -------- Act --------
      stepEventService.handleExternalUpdateEvent(event);

      // -------- Assert --------
      verify(stepService, never()).saveStep(any());
    }

    @Test
    void shouldDoNothing_whenUpdateReturnsOptionalEmpty() throws ChainingException {
      // -------- Prepare --------
      ExternalUpdateEvent event = mock(ExternalUpdateEvent.class);
      String stepRunId = UUID.randomUUID().toString();
      when(event.getStepId()).thenReturn(stepRunId);

      Step stepRun = mock(Step.class);
      when(stepRun.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);

      when(stepService.findByIdAndStatus(stepRunId, StepStatus.RUN)).thenReturn(stepRun);

      ActionStep actionStep = mock(ActionStep.class);
      when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, null))
          .thenReturn(actionStep);
      when(actionStep.update(stepRun)).thenReturn(Optional.empty());

      // -------- Act --------
      stepEventService.handleExternalUpdateEvent(event);

      // -------- Assert --------
      verify(actionStep).update(stepRun);
      verify(stepService, never()).saveStep(any());
    }

    @Test
    void given_updateReturnsPresent_should_saveAndEvaluateProgress() throws ChainingException {
      // Arrange
      ExternalUpdateEvent event = mock(ExternalUpdateEvent.class);
      String stepRunId = UUID.randomUUID().toString();
      when(event.getStepId()).thenReturn(stepRunId);

      Step stepRun = mock(Step.class);
      when(stepRun.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
      when(stepRun.getId()).thenReturn(stepRunId);

      when(stepService.findByIdAndStatus(stepRunId, StepStatus.RUN)).thenReturn(stepRun);

      ActionStep localActionStep = mock(ActionStep.class);
      when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, stepRunId))
          .thenReturn(localActionStep);

      Step updated = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(updated.getWorkflow()).thenReturn(workflowRun);
      when(localActionStep.update(stepRun)).thenReturn(Optional.of(updated));

      // Act
      stepEventService.handleExternalUpdateEvent(event);

      // Assert
      verify(stepService).saveStep(updated);
      verify(stepService).evaluateWorkflowProgress(workflowRun);
      verify(workflowService).saveWorkflowRun(workflowRun);
    }
  }
}
