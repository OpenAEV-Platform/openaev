package io.openaev.service.chaining;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.ActionStep;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepActionClass;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StepEventServiceTest {

  @Mock private ChainingConfig chainingConfig;
  @Mock private StepService stepService;
  @Mock private WorkflowService workflowService;
  @Mock private StepRepository stepRepository;
  @Mock private QueueChainingService queueChainingService;
  @Mock private TenantScopedTransaction tenantTx;
  @Mock private ActionStep actionStep;

  @InjectMocks private StepEventService stepEventService;

  @BeforeEach
  void setUp() {
    lenient().when(chainingConfig.getMaxRetryCount()).thenReturn(3);
    lenient()
        .doAnswer(
            invocation -> {
              Runnable work = invocation.getArgument(1);
              work.run();
              return null;
            })
        .when(tenantTx)
        .execute(any(TxCtx.class), any(Runnable.class));
    // Tenant-propagation tests assert the scope opened around the event, not run() itself; make the
    // step lookup explicitly empty so the primitive's Runnable takes the (harmless) not-found
    // branch.
    lenient().when(stepRepository.findForUpdateById(any())).thenReturn(Optional.empty());
  }

  @AfterEach
  void clearTenantScope() {
    // Belt and suspenders: the consumer clears TenantContext in a finally, but the tests share the
    // JUnit thread, so guarantee no scope leaks between them.
    TenantContext.clearCurrentTenant();
  }

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
    void shouldDropDuplicateWithoutRunningOrTouchingStep_whenStepAlreadyRan()
        throws ChainingException {
      // -------- Prepare --------
      Step stepReady = mock(Step.class);
      ActionStep actionStep = mock(ActionStep.class);

      when(stepReady.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
      when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, null))
          .thenReturn(actionStep);
      when(actionStep.isAlreadyRun(stepReady)).thenReturn(true);

      // -------- Act --------
      stepEventService.run(stepReady);

      // -------- Assert --------
      verify(actionStep, never()).run(any());
      verify(stepReady, never()).setStatus(any());
      verify(stepService, never()).saveStep(any());
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
      when(stepService.findById(stepRunId1)).thenThrow(new ElementNotFoundException("not found"));
      when(stepService.findById(stepRunId2)).thenThrow(new ElementNotFoundException("not found"));

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

      when(stepRepository.findForUpdateById(stepId)).thenReturn(Optional.of(step));
      when(stepService.factoryAction(eq(StepActionClass.INJECT_EXECUTION), any()))
          .thenReturn(actionStep);
      when(actionStep.run(step)).thenReturn(Optional.of(stepRun));

      // Act
      stepEventService.handleReadyStepEvent(event);

      // Assert
      verify(stepRepository).findForUpdateById(stepId);
      assertEquals(StepStatus.RUN, stepRun.getStatus());
      verify(stepService).saveStep(stepRun);
    }

    @Test
    void given_missingStep_should_notRun() {
      // Arrange
      StepEvent event = mock(StepEvent.class);
      String stepId = UUID.randomUUID().toString();
      when(event.getStepId()).thenReturn(stepId);

      when(stepRepository.findForUpdateById(stepId)).thenReturn(Optional.empty());

      // Act
      stepEventService.handleReadyStepEvent(event);

      // Assert
      verify(stepRepository).findForUpdateById(stepId);
      verify(stepService, never()).saveStep(any());
    }

    @Test
    void given_stepExecutionFailed_should_endStepWithoutRequeue() throws Exception {
      // Arrange: the action step reports a final failure (e.g. no agent could run the inject)
      StepEvent event = StepEvent.builder().stepId(UUID.randomUUID().toString()).build();
      Step step = new Step();
      step.setStepAction(StepActionClass.INJECT_EXECUTION);
      when(stepRepository.findForUpdateById(event.getStepId())).thenReturn(Optional.of(step));
      when(stepService.factoryAction(eq(StepActionClass.INJECT_EXECUTION), any()))
          .thenReturn(actionStep);
      when(actionStep.run(step)).thenReturn(Optional.empty());

      // Act
      stepEventService.handleReadyStepEvent(event);

      // Assert: the step ends and the same event is never re-published
      assertEquals(StepStatus.END, step.getStatus());
      verify(stepService).saveStep(step);
      verify(queueChainingService, never()).republishReadyEvent(any());
      assertEquals(0, event.getRetryCount());
    }
  }

  // -- RETRY ON TRANSACTIONAL FAILURE --

  @Nested
  class RetryOnTransactionalFailure {

    @Test
    void given_transactionFailure_should_requeue_whenRetryCountBelowMax() throws IOException {
      // Arrange
      StepEvent event = StepEvent.builder().stepId(UUID.randomUUID().toString()).build();
      assertEquals(0, event.getRetryCount());

      doAnswer(
              invocation -> {
                throw new RuntimeException("DB error");
              })
          .when(tenantTx)
          .execute(any(TxCtx.class), any(Runnable.class));

      // Act
      stepEventService.handleReadyStepEvent(event);

      // Assert
      assertEquals(1, event.getRetryCount());
      verify(queueChainingService).republishReadyEvent(event);
    }

    @Test
    void given_transactionFailure_should_drop_whenMaxRetriesReached() throws IOException {
      // Arrange
      StepEvent event = StepEvent.builder().stepId(UUID.randomUUID().toString()).build();
      event.setRetryCount(chainingConfig.getMaxRetryCount());

      doAnswer(
              invocation -> {
                throw new RuntimeException("DB error");
              })
          .when(tenantTx)
          .execute(any(TxCtx.class), any(Runnable.class));

      // Act
      stepEventService.handleReadyStepEvent(event);

      // Assert — event is dropped, not re-queued; ending the step failed too, so it stays READY
      verify(queueChainingService, never()).republishReadyEvent(any());
      verify(stepService, never()).saveStep(any());
    }

    @Test
    void given_maxRetriesReached_should_endReadyStep() throws IOException {
      // Arrange: the event transaction fails, the transaction ending the step succeeds
      StepEvent event = StepEvent.builder().stepId(UUID.randomUUID().toString()).build();
      event.setRetryCount(chainingConfig.getMaxRetryCount());
      Step step = new Step();
      step.setStatus(StepStatus.READY);
      when(stepRepository.findForUpdateById(event.getStepId())).thenReturn(Optional.of(step));

      doThrow(new RuntimeException("DB error"))
          .doAnswer(
              invocation -> {
                Runnable work = invocation.getArgument(1);
                work.run();
                return null;
              })
          .when(tenantTx)
          .execute(any(TxCtx.class), any(Runnable.class));

      // Act
      stepEventService.handleReadyStepEvent(event);

      // Assert: the step ends instead of staying READY with no event left to carry it
      assertEquals(StepStatus.END, step.getStatus());
      verify(stepService).saveStep(step);
      verify(queueChainingService, never()).republishReadyEvent(any());
    }

    @Test
    void given_maxRetriesReached_andStepNoLongerReady_should_leaveStepUntouched() {
      // Arrange: another event moved the step to RUN in the meantime
      StepEvent event = StepEvent.builder().stepId(UUID.randomUUID().toString()).build();
      event.setRetryCount(chainingConfig.getMaxRetryCount());
      Step step = new Step();
      step.setStatus(StepStatus.RUN);
      when(stepRepository.findForUpdateById(event.getStepId())).thenReturn(Optional.of(step));

      doThrow(new RuntimeException("DB error"))
          .doAnswer(
              invocation -> {
                Runnable work = invocation.getArgument(1);
                work.run();
                return null;
              })
          .when(tenantTx)
          .execute(any(TxCtx.class), any(Runnable.class));

      // Act
      stepEventService.handleReadyStepEvent(event);

      // Assert
      assertEquals(StepStatus.RUN, step.getStatus());
      verify(stepService, never()).saveStep(any());
    }

    @Test
    void given_transactionFailure_andRepublishFails_should_logAndNotThrow() throws IOException {
      // Arrange
      StepEvent event = StepEvent.builder().stepId(UUID.randomUUID().toString()).build();

      doAnswer(
              invocation -> {
                throw new RuntimeException("DB error");
              })
          .when(tenantTx)
          .execute(any(TxCtx.class), any(Runnable.class));

      doThrow(new IOException("RabbitMQ down"))
          .when(queueChainingService)
          .republishReadyEvent(any());

      // Act — should not throw
      stepEventService.handleReadyStepEvent(event);

      // Assert
      assertEquals(1, event.getRetryCount());
      verify(queueChainingService).republishReadyEvent(event);
    }

    @Test
    void given_republishFails_should_endReadyStep() throws IOException {
      // Arrange: the event transaction fails, its re-publication fails, ending the step succeeds
      StepEvent event = StepEvent.builder().stepId(UUID.randomUUID().toString()).build();
      Step step = new Step();
      step.setStatus(StepStatus.READY);
      when(stepRepository.findForUpdateById(event.getStepId())).thenReturn(Optional.of(step));

      doThrow(new RuntimeException("DB error"))
          .doAnswer(
              invocation -> {
                Runnable work = invocation.getArgument(1);
                work.run();
                return null;
              })
          .when(tenantTx)
          .execute(any(TxCtx.class), any(Runnable.class));
      doThrow(new IOException("RabbitMQ down"))
          .when(queueChainingService)
          .republishReadyEvent(any());

      // Act
      stepEventService.handleReadyStepEvent(event);

      // Assert: the step ends instead of staying READY with no event left to carry it
      assertEquals(StepStatus.END, step.getStatus());
      verify(stepService).saveStep(step);
    }

    @Test
    void given_republishThrowsRuntimeException_should_notStopTheBatch() throws IOException {
      // Arrange: every event transaction fails, and the first re-publication hits a closed channel
      StepEvent first = StepEvent.builder().stepId(UUID.randomUUID().toString()).build();
      StepEvent second = StepEvent.builder().stepId(UUID.randomUUID().toString()).build();

      doAnswer(
              invocation -> {
                throw new RuntimeException("DB error");
              })
          .when(tenantTx)
          .execute(any(TxCtx.class), any(Runnable.class));
      doThrow(new IllegalStateException("channel is already closed"))
          .doNothing()
          .when(queueChainingService)
          .republishReadyEvent(any());

      // Act — should not throw
      stepEventService.handleReadyEvent(List.of(first, second));

      // Assert: the second event is still handled
      verify(queueChainingService).republishReadyEvent(first);
      verify(queueChainingService).republishReadyEvent(second);
    }
  }

  @Nested
  class RetryOnEndCheckFailure {

    @Test
    void given_endCheckFails_should_requeue_whenRetryCountBelowMax()
        throws IOException, ChainingException {
      // Arrange
      ExternalUpdateEvent event =
          ExternalUpdateEvent.builder().stepId(UUID.randomUUID().toString()).build();
      assertEquals(0, event.getRetryCount());

      Step stepRun = mock(Step.class);
      when(stepRun.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
      when(stepService.findById(event.getStepId())).thenReturn(stepRun);
      when(stepRun.getStatus()).thenReturn(StepStatus.RUN);
      when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, null))
          .thenReturn(actionStep);

      Step stepUpdated = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(stepUpdated.getWorkflow()).thenReturn(workflowRun);
      when(actionStep.update(stepRun)).thenReturn(Optional.of(stepUpdated));
      doThrow(new ChainingException("end() failed")).when(actionStep).end(stepUpdated);

      // Act
      stepEventService.handleExternalUpdateEvent(event);

      // Assert
      assertEquals(1, event.getRetryCount());
      verify(queueChainingService).republishUpdateEvent(event);
      // update()'s result is still persisted despite end() failing
      verify(stepService).saveStep(stepUpdated);
    }

    @Test
    void given_endCheckFails_should_drop_whenMaxRetriesReached()
        throws IOException, ChainingException {
      // Arrange
      ExternalUpdateEvent event =
          ExternalUpdateEvent.builder().stepId(UUID.randomUUID().toString()).build();
      event.setRetryCount(chainingConfig.getMaxRetryCount());

      Step stepRun = mock(Step.class);
      when(stepRun.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
      when(stepService.findById(event.getStepId())).thenReturn(stepRun);
      when(stepRun.getStatus()).thenReturn(StepStatus.RUN);
      when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, null))
          .thenReturn(actionStep);

      Step stepUpdated = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(stepUpdated.getWorkflow()).thenReturn(workflowRun);
      when(actionStep.update(stepRun)).thenReturn(Optional.of(stepUpdated));
      doThrow(new ChainingException("end() failed")).when(actionStep).end(stepUpdated);

      // Act
      stepEventService.handleExternalUpdateEvent(event);

      // Assert — event dropped, not re-queued, but step is still saved (RUN, self-healing on next
      // event)
      verify(queueChainingService, never()).republishUpdateEvent(any());
      verify(stepService).saveStep(stepUpdated);
    }

    @Test
    void given_endCheckFails_andRepublishFails_should_logAndNotThrow()
        throws IOException, ChainingException {
      // Arrange
      ExternalUpdateEvent event =
          ExternalUpdateEvent.builder().stepId(UUID.randomUUID().toString()).build();

      Step stepRun = mock(Step.class);
      when(stepRun.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
      when(stepService.findById(event.getStepId())).thenReturn(stepRun);
      when(stepRun.getStatus()).thenReturn(StepStatus.RUN);
      when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, null))
          .thenReturn(actionStep);

      Step stepUpdated = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(stepUpdated.getWorkflow()).thenReturn(workflowRun);
      when(actionStep.update(stepRun)).thenReturn(Optional.of(stepUpdated));
      doThrow(new ChainingException("end() failed")).when(actionStep).end(stepUpdated);
      doThrow(new IOException("RabbitMQ down"))
          .when(queueChainingService)
          .republishUpdateEvent(any());

      // Act — should not throw
      stepEventService.handleExternalUpdateEvent(event);

      // Assert
      assertEquals(1, event.getRetryCount());
      verify(queueChainingService).republishUpdateEvent(event);
    }
  }

  // -- TENANT PROPAGATION (#6357) --

  @Nested
  class TenantPropagation {

    // The chaining worker carries no tenant; the fix restores it from the event on BOTH mechanisms:
    // the MT v2 primitive (GUC, tenant-active writes) and the v1 TenantContext (@Filter reads).
    // These
    // pin the propagation: the consumer MUST open the scope under the event's tenant (and the
    // default
    // when absent), and must clear TenantContext after, so a regression that drops either is
    // caught.

    @Test
    void handleReadyStepEvent_opensTheScopeUnderTheEventTenant() {
      StepEvent event =
          StepEvent.builder().stepId(UUID.randomUUID().toString()).tenantId("tenant-B").build();

      stepEventService.handleReadyStepEvent(event);

      verify(tenantTx).execute(eq(TxCtx.forTenant("tenant-B")), any(Runnable.class));
    }

    @Test
    void handleReadyStepEvent_scopesV1TenantContextDuringWork_andClearsAfter() {
      StepEvent event =
          StepEvent.builder().stepId(UUID.randomUUID().toString()).tenantId("tenant-B").build();
      // Capture the v1 @Filter scope (TenantContext) visible while the primitive's work runs: the
      // v1
      // entities the run reads (injector contract, endpoints, assets) resolve through it, not the
      // GUC.
      AtomicReference<String> seenDuringWork = new AtomicReference<>();
      doAnswer(
              invocation -> {
                seenDuringWork.set(TenantContext.getCurrentTenant());
                Runnable work = invocation.getArgument(1);
                work.run();
                return null;
              })
          .when(tenantTx)
          .execute(any(TxCtx.class), any(Runnable.class));

      stepEventService.handleReadyStepEvent(event);

      assertEquals("tenant-B", seenDuringWork.get());
      assertFalse(TenantContext.hasCurrentTenant(), "TenantContext must not leak past the event");
    }

    @Test
    void handleReadyStepEvent_nullEventTenant_fallsBackToDefault() {
      StepEvent event = StepEvent.builder().stepId(UUID.randomUUID().toString()).build();

      stepEventService.handleReadyStepEvent(event);

      verify(tenantTx).execute(eq(TxCtx.forTenant(DEFAULT_TENANT_UUID)), any(Runnable.class));
    }

    @Test
    void handleExternalUpdateEvent_opensTheScopeUnderTheEventTenant() {
      ExternalUpdateEvent event =
          ExternalUpdateEvent.builder()
              .stepId(UUID.randomUUID().toString())
              .tenantId("tenant-B")
              .build();
      // Short-circuit the body: this test only asserts the tenant scope is opened, not the update
      // logic (the setUp stub runs the primitive's Runnable, which would otherwise NPE on a null
      // step).
      when(stepService.findById(any())).thenThrow(new ElementNotFoundException("short-circuit"));

      stepEventService.handleExternalUpdateEvent(event);

      verify(tenantTx).execute(eq(TxCtx.forTenant("tenant-B")), any(Runnable.class));
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

      when(stepService.findById(stepRunId)).thenReturn(stepRun);
      when(stepRun.getStatus()).thenReturn(StepStatus.RUN);

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

      when(stepService.findById(stepRunId)).thenThrow(new ElementNotFoundException("not found"));

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

      when(stepService.findById(stepRunId)).thenReturn(stepRun);
      when(stepRun.getStatus()).thenReturn(StepStatus.RUN);

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

      when(stepService.findById(stepRunId)).thenReturn(stepRun);
      when(stepRun.getStatus()).thenReturn(StepStatus.RUN);

      ActionStep localActionStep = mock(ActionStep.class);
      when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, stepRunId))
          .thenReturn(localActionStep);

      Step updated = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(updated.getWorkflow()).thenReturn(workflowRun);
      when(localActionStep.update(stepRun)).thenReturn(Optional.of(updated));

      when(workflowService.evaluateWorkflowProgress(workflowRun)).thenReturn(workflowRun);

      // Act
      stepEventService.handleExternalUpdateEvent(event);

      // Assert
      verify(stepService).saveStep(updated);
      verify(workflowService).evaluateWorkflowProgress(workflowRun);
      verify(workflowService).saveWorkflowRun(workflowRun);
    }
  }

  // -- THE WORKFLOW RUN IS EVALUATED ONCE THE STEP IS COMMITTED --

  @Nested
  class EvaluationAfterCommit {

    private final AtomicInteger transactions = new AtomicInteger();
    private final AtomicInteger savedIn = new AtomicInteger();
    private final AtomicInteger evaluatedIn = new AtomicInteger();

    /** Counts the transactions; the first ones given fail, as the event transaction may. */
    private void transactionsCounted(int failing) {
      AtomicInteger calls = new AtomicInteger();
      doAnswer(
              invocation -> {
                if (calls.incrementAndGet() <= failing) {
                  throw new RuntimeException("DB error");
                }
                transactions.incrementAndGet();
                Runnable work = invocation.getArgument(1);
                work.run();
                return null;
              })
          .when(tenantTx)
          .execute(any(TxCtx.class), any(Runnable.class));
      lenient()
          .when(stepService.saveStep(any()))
          .thenAnswer(
              invocation -> {
                savedIn.set(transactions.get());
                return invocation.getArgument(0);
              });
    }

    private void evaluationRecorded(Workflow workflowRun, boolean fails) throws ChainingException {
      evaluationRecorded(workflowRun, fails ? Integer.MAX_VALUE : 0);
    }

    /** The evaluation fails its first {@code failing} times, then succeeds. */
    private void evaluationRecorded(Workflow workflowRun, int failing) throws ChainingException {
      AtomicInteger attempts = new AtomicInteger();
      when(workflowService.evaluateWorkflowProgress(workflowRun))
          .thenAnswer(
              invocation -> {
                evaluatedIn.set(transactions.get());
                if (attempts.incrementAndGet() <= failing) {
                  throw new ChainingException("evaluation failed");
                }
                return workflowRun;
              });
    }

    private Step stepOf(Workflow workflowRun, StepStatus status) {
      Step step = new Step();
      step.setId(UUID.randomUUID().toString());
      step.setStepAction(StepActionClass.INJECT_EXECUTION);
      step.setStatus(status);
      step.setWorkflow(workflowRun);
      return step;
    }

    private ExternalUpdateEvent updateOf(Step stepRun) {
      ExternalUpdateEvent event = mock(ExternalUpdateEvent.class);
      when(event.getStepId()).thenReturn(stepRun.getId());
      when(stepService.findById(stepRun.getId())).thenReturn(stepRun);
      return event;
    }

    @Test
    void given_runFails_should_endStepThenEvaluateInATransactionOfItsOwn()
        throws ChainingException {
      // Arrange
      transactionsCounted(0);
      Workflow workflowRun = new Workflow();
      Step stepReady = stepOf(workflowRun, StepStatus.READY);
      when(stepRepository.findForUpdateById(stepReady.getId())).thenReturn(Optional.of(stepReady));
      when(stepService.factoryAction(any(), any())).thenReturn(actionStep);
      when(actionStep.run(stepReady)).thenReturn(Optional.empty());
      evaluationRecorded(workflowRun, false);

      // Act
      stepEventService.handleReadyStepEvent(StepEvent.builder().stepId(stepReady.getId()).build());

      // Assert: a step that depends on it can start now that it has ended
      assertEquals(StepStatus.END, stepReady.getStatus());
      assertEquals(1, savedIn.get());
      assertEquals(2, evaluatedIn.get());
      verify(workflowService).saveWorkflowRun(workflowRun);
    }

    @Test
    void given_runSucceeds_should_notEvaluate() throws ChainingException {
      // Arrange
      transactionsCounted(0);
      Workflow workflowRun = new Workflow();
      Step stepReady = stepOf(workflowRun, StepStatus.READY);
      when(stepRepository.findForUpdateById(stepReady.getId())).thenReturn(Optional.of(stepReady));
      when(stepService.factoryAction(any(), any())).thenReturn(actionStep);
      when(actionStep.run(stepReady)).thenReturn(Optional.of(stepReady));

      // Act
      stepEventService.handleReadyStepEvent(StepEvent.builder().stepId(stepReady.getId()).build());

      // Assert: a running step is evaluated once it is updated, as before
      assertEquals(StepStatus.RUN, stepReady.getStatus());
      verify(workflowService, never()).evaluateWorkflowProgress(any());
    }

    @Test
    void given_updateFails_should_endStepThenEvaluateInATransactionOfItsOwn()
        throws ChainingException {
      // Arrange
      transactionsCounted(0);
      Workflow workflowRun = new Workflow();
      Step stepRun = stepOf(workflowRun, StepStatus.RUN);
      ExternalUpdateEvent event = updateOf(stepRun);
      when(stepService.factoryAction(any(), any()))
          .thenThrow(new ChainingException("update failed"));
      evaluationRecorded(workflowRun, false);

      // Act
      stepEventService.handleExternalUpdateEvent(event);

      // Assert
      assertEquals(StepStatus.END, stepRun.getStatus());
      assertEquals(1, savedIn.get());
      assertEquals(2, evaluatedIn.get());
      verify(workflowService).saveWorkflowRun(workflowRun);
    }

    @Test
    void given_updateSucceeds_should_saveThenEvaluateInATransactionOfItsOwn()
        throws ChainingException {
      // Arrange: two steps ending at once each evaluate after their own commit, so the last to
      // commit sees the other ended
      transactionsCounted(0);
      Workflow workflowRun = new Workflow();
      Step stepRun = stepOf(workflowRun, StepStatus.RUN);
      ExternalUpdateEvent event = updateOf(stepRun);
      when(stepService.factoryAction(any(), any())).thenReturn(actionStep);
      when(actionStep.update(stepRun)).thenReturn(Optional.of(stepRun));
      evaluationRecorded(workflowRun, false);

      // Act
      stepEventService.handleExternalUpdateEvent(event);

      // Assert
      assertEquals(1, savedIn.get());
      assertEquals(2, evaluatedIn.get());
      verify(workflowService).saveWorkflowRun(workflowRun);
    }

    @Test
    void given_readyEventDropped_should_endStepThenEvaluateInATransactionOfItsOwn()
        throws ChainingException {
      // Arrange: the event transaction fails for good, the transaction ending the step succeeds
      transactionsCounted(1);
      Workflow workflowRun = new Workflow();
      Step step = stepOf(workflowRun, StepStatus.READY);
      StepEvent event = StepEvent.builder().stepId(step.getId()).build();
      event.setRetryCount(chainingConfig.getMaxRetryCount());
      when(stepRepository.findForUpdateById(step.getId())).thenReturn(Optional.of(step));
      evaluationRecorded(workflowRun, false);

      // Act
      stepEventService.handleReadyStepEvent(event);

      // Assert
      assertEquals(StepStatus.END, step.getStatus());
      assertEquals(1, savedIn.get());
      assertEquals(2, evaluatedIn.get());
    }

    @Test
    void given_evaluationFails_should_keepTheCommittedStepAndNotThrow() throws ChainingException {
      // Arrange
      transactionsCounted(0);
      Workflow workflowRun = new Workflow();
      Step stepRun = stepOf(workflowRun, StepStatus.RUN);
      ExternalUpdateEvent event = updateOf(stepRun);
      when(stepService.factoryAction(any(), any()))
          .thenThrow(new ChainingException("update failed"));
      evaluationRecorded(workflowRun, true);

      // Act
      assertDoesNotThrow(() -> stepEventService.handleExternalUpdateEvent(event));

      // Assert: the step was saved in a transaction the evaluation never joined, and the
      // evaluation was tried again a bounded number of times, each in a transaction of its own
      assertEquals(StepStatus.END, stepRun.getStatus());
      assertEquals(1, savedIn.get());
      assertEquals(1 + chainingConfig.getMaxRetryCount(), evaluatedIn.get() - 1);
      verify(workflowService, times(1 + chainingConfig.getMaxRetryCount()))
          .evaluateWorkflowProgress(workflowRun);
      verify(workflowService, never()).saveWorkflowRun(any());
    }

    @Test
    void given_evaluationFailsOnce_should_evaluateAgainAndSave() throws ChainingException {
      // Arrange: a transient failure (a deadlock, say) would otherwise leave a dependent step
      // blocked until the workflow's timeout, no later event evaluating the run
      transactionsCounted(0);
      Workflow workflowRun = new Workflow();
      Step stepRun = stepOf(workflowRun, StepStatus.RUN);
      ExternalUpdateEvent event = updateOf(stepRun);
      when(stepService.factoryAction(any(), any())).thenReturn(actionStep);
      when(actionStep.update(stepRun)).thenReturn(Optional.of(stepRun));
      evaluationRecorded(workflowRun, 1);

      // Act
      stepEventService.handleExternalUpdateEvent(event);

      // Assert
      verify(workflowService, times(2)).evaluateWorkflowProgress(workflowRun);
      verify(workflowService).saveWorkflowRun(workflowRun);
      assertEquals(3, evaluatedIn.get());
    }

    @Test
    void given_stepWithoutWorkflow_should_endStepWithoutEvaluating() throws ChainingException {
      // Arrange
      transactionsCounted(0);
      Step stepReady = stepOf(null, StepStatus.READY);
      when(stepRepository.findForUpdateById(stepReady.getId())).thenReturn(Optional.of(stepReady));
      when(stepService.factoryAction(any(), any())).thenReturn(actionStep);
      when(actionStep.run(stepReady)).thenReturn(Optional.empty());

      // Act
      stepEventService.handleReadyStepEvent(StepEvent.builder().stepId(stepReady.getId()).build());

      // Assert
      assertEquals(StepStatus.END, stepReady.getStatus());
      assertEquals(1, transactions.get());
      verify(workflowService, never()).evaluateWorkflowProgress(any());
    }
  }
}
