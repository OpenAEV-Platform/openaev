package io.openaev.service.chaining;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
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
    lenient().when(stepRepository.findById(any())).thenReturn(Optional.empty());
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

      // Assert — event is dropped, not re-queued
      verify(queueChainingService, never()).republishReadyEvent(any());
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
      when(stepService.findByIdAndStatus(any(), any()))
          .thenThrow(new ElementNotFoundException("short-circuit"));

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
      verify(workflowService).evaluateWorkflowProgress(workflowRun);
      verify(workflowService).saveWorkflowRun(workflowRun);
    }
  }
}
