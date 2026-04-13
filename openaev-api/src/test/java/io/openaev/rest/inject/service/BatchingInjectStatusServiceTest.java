package io.openaev.rest.inject.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Agent;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.helper.queue.BatchQueueService;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.form.InjectExecutionInput;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchingInjectStatusService Tests")
class BatchingInjectStatusServiceTest {

  @Mock private InjectRepository injectRepository;
  @Mock private AgentRepository agentRepository;
  @Mock private StructuredOutputUtils structuredOutputUtils;
  @Mock private InjectExecutionService injectExecutionService;

  @Mock private BatchQueueService<InjectExecutionCallback> injectTraceQueueService;

  @InjectMocks private BatchingInjectStatusService service;

  private static final String INJECT_ID = "inject-1";
  private static final String AGENT_ID = "agent-1";
  private static final int MAX_RETRIES = 5;

  @BeforeEach
  void wireQueueService() {
    // @InjectMocks does not set non-final fields with @Setter; wire it explicitly for the tests
    // that exercise the requeue path. Tests that want to exercise the null-guard branch can
    // override this via service.setInjectTraceQueueService(null).
    service.setInjectTraceQueueService(injectTraceQueueService);
  }

  private Inject createInjectWithPendingStatus(String injectId) {
    Inject inject = new Inject();
    inject.setId(injectId);
    InjectStatus status = new InjectStatus();
    status.setName(ExecutionStatus.PENDING);
    inject.setStatus(status);
    return inject;
  }

  private Inject createInjectWithExecutingStatus(String injectId) {
    Inject inject = new Inject();
    inject.setId(injectId);
    InjectStatus status = new InjectStatus();
    status.setName(ExecutionStatus.EXECUTING);
    inject.setStatus(status);
    return inject;
  }

  private Agent createAgent(String agentId) {
    Agent agent = new Agent();
    agent.setId(agentId);
    return agent;
  }

  private InjectExecutionCallback createCallback(
      String injectId, String agentId, InjectExecutionAction action, long emissionDate) {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setAction(action);
    input.setMessage("test message");
    input.setStatus("SUCCESS");
    return InjectExecutionCallback.builder()
        .injectId(injectId)
        .agentId(agentId)
        .injectExecutionInput(input)
        .emissionDate(emissionDate)
        .build();
  }

  // ========================================================================
  // Chronological ordering
  // ========================================================================
  @Nested
  @DisplayName("Chronological ordering")
  class ChronologicalOrderingTests {

    @Test
    @DisplayName("should process callbacks in chronological order by emission date")
    void shouldProcessInChronologicalOrder() {
      Inject inject = createInjectWithPendingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      // Create callbacks out of order
      InjectExecutionCallback late =
          createCallback(INJECT_ID, AGENT_ID, InjectExecutionAction.command_execution, 3000L);
      InjectExecutionCallback early =
          createCallback(INJECT_ID, AGENT_ID, InjectExecutionAction.command_execution, 1000L);
      InjectExecutionCallback middle =
          createCallback(INJECT_ID, AGENT_ID, InjectExecutionAction.command_execution, 2000L);

      service.handleInjectExecutionCallback(List.of(late, early, middle));

      // Verify processInjectExecutionWithAgent was called 3 times in chronological order
      InOrder inOrder = inOrder(injectExecutionService);
      inOrder
          .verify(injectExecutionService)
          .processInjectExecutionWithAgent(
              eq(inject), eq(agent), argThat(input -> input == early.getInjectExecutionInput()));
      inOrder
          .verify(injectExecutionService)
          .processInjectExecutionWithAgent(
              eq(inject), eq(agent), argThat(input -> input == middle.getInjectExecutionInput()));
      inOrder
          .verify(injectExecutionService)
          .processInjectExecutionWithAgent(
              eq(inject), eq(agent), argThat(input -> input == late.getInjectExecutionInput()));
    }
  }

  // ========================================================================
  // ElementNotFoundException handling
  // ========================================================================
  @Nested
  @DisplayName("ElementNotFoundException handling")
  class ElementNotFoundTests {

    @Test
    @DisplayName("should handle missing inject gracefully and add to success list")
    void shouldHandleMissingInject() {
      // Return empty list — no inject found
      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of());
      when(agentRepository.findAllById(anyList())).thenReturn(List.of());

      InjectExecutionCallback callback =
          createCallback(
              "non-existent-inject", AGENT_ID, InjectExecutionAction.command_execution, 1000L);

      List<InjectExecutionCallback> result =
          service.handleInjectExecutionCallback(List.of(callback));

      // Should be in success list because ElementNotFoundException is caught and handled
      assertEquals(1, result.size());
      verify(injectExecutionService).handleInjectExecutionError(isNull(), any(Exception.class));
    }

    @Test
    @DisplayName("should handle missing agent gracefully and add to success list")
    void shouldHandleMissingAgent() {
      Inject inject = createInjectWithPendingStatus(INJECT_ID);

      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      // Return empty — no agent found
      when(agentRepository.findAllById(anyList())).thenReturn(List.of());

      InjectExecutionCallback callback =
          createCallback(
              INJECT_ID, "non-existent-agent", InjectExecutionAction.command_execution, 1000L);

      List<InjectExecutionCallback> result =
          service.handleInjectExecutionCallback(List.of(callback));

      // Missing agent throws ElementNotFoundException → caught → added to success
      assertEquals(1, result.size());
      verify(injectExecutionService).handleInjectExecutionError(eq(inject), any(Exception.class));
    }
  }

  // ========================================================================
  // General exception handling
  // ========================================================================
  @Nested
  @DisplayName("General exception handling")
  class GeneralExceptionTests {

    @Test
    @DisplayName("should not add callback to success list when general exception occurs")
    void shouldNotAddToSuccessListOnGeneralException() {
      Inject inject = createInjectWithPendingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      doThrow(new RuntimeException("Unexpected error"))
          .when(injectExecutionService)
          .processInjectExecutionWithAgent(any(), any(), any());

      InjectExecutionCallback callback =
          createCallback(INJECT_ID, AGENT_ID, InjectExecutionAction.command_execution, 1000L);

      List<InjectExecutionCallback> result =
          service.handleInjectExecutionCallback(List.of(callback));

      // General exception → NOT added to success list
      assertTrue(result.isEmpty());
    }
  }

  // ========================================================================
  // PENDING state guard
  // ========================================================================
  @Nested
  @DisplayName("PENDING state guard")
  class PendingStateGuardTests {

    @Test
    @DisplayName("should not process complete action for non-PENDING inject and queue it for retry")
    void shouldRejectCompleteActionForNonPendingInject() {
      // Inject is in EXECUTING state, not PENDING
      Inject inject = createInjectWithExecutingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      InjectExecutionCallback callback =
          createCallback(INJECT_ID, AGENT_ID, InjectExecutionAction.complete, 1000L);

      List<InjectExecutionCallback> result =
          service.handleInjectExecutionCallback(List.of(callback));

      // The callback is queued for retry → NOT added to the successfully-processed list,
      // and no execution processing happened.
      assertTrue(result.isEmpty());
      verify(injectExecutionService, never()).processInjectExecutionWithAgent(any(), any(), any());
      verify(injectExecutionService, never()).processInjectExecutionWithInjector(any(), any());
      // retry counter is bumped on the callback instance
      assertEquals(1, callback.getRetryCount());
    }

    @Test
    @DisplayName("should allow complete action for PENDING inject")
    void shouldAllowCompleteActionForPendingInject() {
      Inject inject = createInjectWithPendingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      InjectExecutionCallback callback =
          createCallback(INJECT_ID, AGENT_ID, InjectExecutionAction.complete, 1000L);

      List<InjectExecutionCallback> result =
          service.handleInjectExecutionCallback(List.of(callback));

      assertEquals(1, result.size());
      verify(injectExecutionService)
          .processInjectExecutionWithAgent(
              eq(inject), eq(agent), eq(callback.getInjectExecutionInput()));
    }

    @Test
    @DisplayName("should allow non-complete actions regardless of inject status")
    void shouldAllowNonCompleteActionsRegardlessOfStatus() {
      Inject inject = createInjectWithExecutingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      // command_execution action should pass regardless of status
      InjectExecutionCallback callback =
          createCallback(INJECT_ID, AGENT_ID, InjectExecutionAction.command_execution, 1000L);

      List<InjectExecutionCallback> result =
          service.handleInjectExecutionCallback(List.of(callback));

      assertEquals(1, result.size());
      verify(injectExecutionService)
          .processInjectExecutionWithAgent(
              eq(inject), eq(agent), eq(callback.getInjectExecutionInput()));
    }
  }

  // ========================================================================
  // Bulk loading optimization
  // ========================================================================
  @Nested
  @DisplayName("Bulk loading optimization")
  class BulkLoadingTests {

    @Test
    @DisplayName("should load all injects and agents in a single query each")
    void shouldBulkLoadInjectsAndAgents() {
      Inject inject1 = createInjectWithPendingStatus("inject-1");
      Inject inject2 = createInjectWithPendingStatus("inject-2");
      Agent agent1 = createAgent("agent-1");
      Agent agent2 = createAgent("agent-2");

      when(injectRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(inject1, inject2));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent1, agent2));

      InjectExecutionCallback callback1 =
          createCallback("inject-1", "agent-1", InjectExecutionAction.command_execution, 1000L);
      InjectExecutionCallback callback2 =
          createCallback("inject-2", "agent-2", InjectExecutionAction.command_execution, 2000L);

      service.handleInjectExecutionCallback(List.of(callback1, callback2));

      // Verify bulk loads are called exactly once each
      verify(injectRepository, times(1)).findAllByIdWithExpectations(anyList());
      verify(agentRepository, times(1)).findAllById(anyList());
    }
  }

  // ========================================================================
  // Successful processing
  // ========================================================================
  @Nested
  @DisplayName("Successful processing")
  class SuccessfulProcessingTests {

    @Test
    @DisplayName("should call processInjectExecutionWithAgent with correct arguments")
    void shouldCallProcessInjectExecutionWithCorrectArgs() {
      Inject inject = createInjectWithPendingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      InjectExecutionCallback callback =
          createCallback(INJECT_ID, AGENT_ID, InjectExecutionAction.command_execution, 1000L);

      List<InjectExecutionCallback> result =
          service.handleInjectExecutionCallback(List.of(callback));

      assertEquals(1, result.size());
      assertSame(callback, result.getFirst());
      verify(injectExecutionService)
          .processInjectExecutionWithAgent(
              eq(inject), eq(agent), eq(callback.getInjectExecutionInput()));
    }

    @Test
    @DisplayName("should handle callback with null agentId by passing null agent")
    void shouldHandleNullAgentId() {
      Inject inject = createInjectWithPendingStatus(INJECT_ID);

      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of());

      InjectExecutionCallback callback =
          createCallback(INJECT_ID, null, InjectExecutionAction.command_execution, 1000L);

      List<InjectExecutionCallback> result =
          service.handleInjectExecutionCallback(List.of(callback));

      assertEquals(1, result.size());
      verify(injectExecutionService)
          .processInjectExecutionWithInjector(eq(inject), eq(callback.getInjectExecutionInput()));
    }
  }

  // ========================================================================
  // Edge cases
  // ========================================================================
  @Nested
  @DisplayName("Edge cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("should handle empty callback list gracefully")
    void shouldHandleEmptyCallbackList() {
      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of());
      when(agentRepository.findAllById(anyList())).thenReturn(List.of());

      List<InjectExecutionCallback> result = service.handleInjectExecutionCallback(List.of());

      assertTrue(result.isEmpty());
      verify(injectExecutionService, never()).processInjectExecutionWithAgent(any(), any(), any());
      verify(injectExecutionService, never()).processInjectExecutionWithInjector(any(), any());
    }

    // ------------------------------------------------------------------
    // Retry / requeue behavior for complete-before-PENDING race condition
    // ------------------------------------------------------------------

    @Test
    @DisplayName("should drain the requeue queue to the external queue service")
    void shouldDrainRequeueQueueOnRequeueCallbacks() throws IOException {
      // First call: inject is not PENDING → callback gets queued for retry
      Inject inject = createInjectWithExecutingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      long originalEmissionDate = 1000L;
      InjectExecutionCallback callback =
          createCallback(INJECT_ID, AGENT_ID, InjectExecutionAction.complete, originalEmissionDate);
      long beforeHandle = Instant.now().toEpochMilli();
      service.handleInjectExecutionCallback(List.of(callback));

      // Drain
      service.requeueCallbacks();

      ArgumentCaptor<InjectExecutionCallback> captor =
          ArgumentCaptor.forClass(InjectExecutionCallback.class);
      verify(injectTraceQueueService).publish(captor.capture());
      assertSame(callback, captor.getValue());
      assertEquals(1, captor.getValue().getRetryCount());
      // emissionDate must be bumped to "now" on retry so the re-queued callback does not
      // compete for ordering against freshly emitted ones.
      assertNotEquals(originalEmissionDate, captor.getValue().getEmissionDate());
      assertTrue(
          captor.getValue().getEmissionDate() >= beforeHandle,
          "emissionDate should have been refreshed at retry time");
    }

    @Test
    @DisplayName(
        "should process a mixed batch: queue the non-PENDING complete for retry and process the "
            + "other callback normally")
    void shouldHandleMixedBatchOfRetryAndNormalCallbacks() {
      // inject-1 is in EXECUTING → complete callback must be queued for retry
      Inject executingInject = createInjectWithExecutingStatus("inject-1");
      // inject-2 is in PENDING → complete callback must be processed normally
      Inject pendingInject = createInjectWithPendingStatus("inject-2");
      Agent agent1 = createAgent("agent-1");
      Agent agent2 = createAgent("agent-2");

      when(injectRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(executingInject, pendingInject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent1, agent2));

      InjectExecutionCallback retryCallback =
          createCallback("inject-1", "agent-1", InjectExecutionAction.complete, 1000L);
      InjectExecutionCallback normalCallback =
          createCallback("inject-2", "agent-2", InjectExecutionAction.complete, 2000L);

      List<InjectExecutionCallback> result =
          service.handleInjectExecutionCallback(List.of(retryCallback, normalCallback));

      // Only the normal callback is in the success list; the retry callback is queued instead.
      assertEquals(1, result.size());
      assertSame(normalCallback, result.getFirst());
      assertEquals(1, retryCallback.getRetryCount());
      assertEquals(0, normalCallback.getRetryCount());

      // Only the pending inject was processed, not the executing one.
      verify(injectExecutionService)
          .processInjectExecutionWithAgent(
              eq(pendingInject), eq(agent2), eq(normalCallback.getInjectExecutionInput()));
      verify(injectExecutionService, never())
          .processInjectExecutionWithAgent(
              eq(executingInject), any(), eq(retryCallback.getInjectExecutionInput()));
    }

    @Test
    @DisplayName(
        "should stop retrying once MAX_RETRIES is reached and still persist the execution trace")
    void shouldPersistExecutionTraceAfterMaxRetries() throws IOException {
      Inject inject = createInjectWithExecutingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      InjectExecutionCallback callback =
          createCallback(INJECT_ID, AGENT_ID, InjectExecutionAction.complete, 1000L);
      // Pretend this callback has already been retried MAX_RETRIES times
      callback.setRetryCount(MAX_RETRIES);

      List<InjectExecutionCallback> result =
          service.handleInjectExecutionCallback(List.of(callback));

      // Max retries reached → fall through to persist the trace as a last-ditch effort,
      // not re-incremented, not re-queued.
      assertEquals(1, result.size());
      assertSame(callback, result.getFirst());
      assertEquals(MAX_RETRIES, callback.getRetryCount());
      verify(injectExecutionService)
          .processInjectExecutionWithAgent(
              eq(inject), eq(agent), eq(callback.getInjectExecutionInput()));
      service.requeueCallbacks();
      verify(injectTraceQueueService, never()).publish(any());
    }

    @Test
    @DisplayName(
        "should persist via injector path when MAX_RETRIES is reached and callback has no agent")
    void shouldPersistViaInjectorPathAfterMaxRetriesWithNullAgent() throws IOException {
      Inject inject = createInjectWithExecutingStatus(INJECT_ID);

      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of());

      InjectExecutionCallback callback =
          createCallback(INJECT_ID, null, InjectExecutionAction.complete, 1000L);
      // Already exhausted retries
      callback.setRetryCount(MAX_RETRIES);

      List<InjectExecutionCallback> result =
          service.handleInjectExecutionCallback(List.of(callback));

      // Max retries reached with no agent → fall through to the injector branch of
      // saveExecutionTrace and still persist the trace.
      assertEquals(1, result.size());
      assertSame(callback, result.getFirst());
      assertEquals(MAX_RETRIES, callback.getRetryCount());
      verify(injectExecutionService)
          .processInjectExecutionWithInjector(eq(inject), eq(callback.getInjectExecutionInput()));
      verify(injectExecutionService, never()).processInjectExecutionWithAgent(any(), any(), any());
      service.requeueCallbacks();
      verify(injectTraceQueueService, never()).publish(any());
    }

    @Test
    @DisplayName("requeueCallbacks is a safe no-op when queue service is not configured")
    void requeueCallbacksIsSafeWhenQueueServiceIsNull() {
      // Simulate the legacy / unconfigured path: no queue service wired.
      service.setInjectTraceQueueService(null);

      Inject inject = createInjectWithExecutingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);
      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      InjectExecutionCallback callback =
          createCallback(INJECT_ID, AGENT_ID, InjectExecutionAction.complete, 1000L);
      service.handleInjectExecutionCallback(List.of(callback));

      // Must not NPE even though the in-memory requeue queue is non-empty.
      assertDoesNotThrow(() -> service.requeueCallbacks());
      verifyNoInteractions(injectTraceQueueService);
    }

    @Test
    @DisplayName("should process duplicate inject callbacks with the same inject entity")
    void shouldProcessDuplicateInjectCallbacksWithSameInjectEntity() {
      Inject inject = createInjectWithPendingStatus(INJECT_ID);
      Agent agent1 = createAgent("agent-1");
      Agent agent2 = createAgent("agent-2");

      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent1, agent2));

      InjectExecutionCallback callback1 =
          createCallback(INJECT_ID, "agent-1", InjectExecutionAction.command_execution, 1000L);
      InjectExecutionCallback callback2 =
          createCallback(INJECT_ID, "agent-2", InjectExecutionAction.command_execution, 2000L);

      List<InjectExecutionCallback> result =
          service.handleInjectExecutionCallback(List.of(callback1, callback2));

      assertEquals(2, result.size());
      // Both callbacks should reference the same Inject entity from the bulk load
      verify(injectExecutionService)
          .processInjectExecutionWithAgent(same(inject), eq(agent1), any());
      verify(injectExecutionService)
          .processInjectExecutionWithAgent(same(inject), eq(agent2), any());
    }
  }
}
