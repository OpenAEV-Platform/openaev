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
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StepEventServiceTest {

  @Mock private StepService stepService;
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
    void shouldConsumeReadyEvents_andReturnSameList() {
      // -------- Prepare --------
      StepEvent e1 = mock(StepEvent.class);
      StepEvent e2 = mock(StepEvent.class);
      List<StepEvent> events = List.of(e1, e2);

      when(e1.getStepId()).thenReturn(UUID.randomUUID().toString());
      when(e2.getStepId()).thenReturn(UUID.randomUUID().toString());

      // -------- Act --------
      List<StepEvent> result = stepEventService.handleReadyEvent(events);

      // -------- Assert --------
      assertSame(events, result);
    }

    @Test
    void shouldConsumeExternalUpdateEvents_andReturnSameList() {
      // -------- Prepare --------
      ExternalUpdateEvent e1 = mock(ExternalUpdateEvent.class);
      ExternalUpdateEvent e2 = mock(ExternalUpdateEvent.class);
      List<ExternalUpdateEvent> events = List.of(e1, e2);

      when(e1.getStepId()).thenReturn(UUID.randomUUID().toString());
      when(e2.getStepId()).thenReturn(UUID.randomUUID().toString());

      // -------- Act --------
      List<ExternalUpdateEvent> result = stepEventService.handleExternalUpdateEvent(events);

      // -------- Assert --------
      assertSame(events, result);
    }
  }

  // -- HANDLE READY STEP EVENT --

  @Nested
  class HandleReadyStepEvent {

    @ParameterizedTest(name = "{index} => stepFound={0}")
    @MethodSource("readyStepEventScenarios")
    void shouldRunOnlyWhenStepExists(boolean stepFound) {
      // -------- Prepare --------
      StepEvent event = mock(StepEvent.class);
      String stepId = UUID.randomUUID().toString();
      when(event.getStepId()).thenReturn(stepId);

      Step step = mock(Step.class);

      when(stepRepository.findById(stepId))
          .thenReturn(stepFound ? Optional.of(step) : Optional.empty());

      // -------- Act --------
      stepEventService.handleReadyStepEvent(event);

      // -------- Assert --------
      verify(stepRepository).findById(stepId);
    }

    static Stream<Arguments> readyStepEventScenarios() {
      return Stream.of(Arguments.of(true), Arguments.of(false));
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
    void shouldSaveUpdatedStep_whenUpdateReturnsPresent() throws ChainingException {
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

      Step updated = mock(Step.class);
      when(actionStep.update(stepRun)).thenReturn(Optional.of(updated));

      // -------- Act --------
      stepEventService.handleExternalUpdateEvent(event);

      // -------- Assert --------
      verify(stepService).saveStep(updated);
    }
  }
}
