package io.openaev.rest.inject.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.form.InjectExecutionInput;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

  @InjectMocks private BatchingInjectStatusService service;

  private static final String INJECT_ID = "inject-1";
  private static final String AGENT_ID = "agent-1";

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

  @BeforeEach
  void setUp() {
    // Default stubs for most tests
    lenient()
        .when(structuredOutputUtils.extractOutputParsers(any(Inject.class)))
        .thenReturn(Set.of());
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

      // Verify processInjectExecution was called 3 times in chronological order
      InOrder inOrder = inOrder(injectExecutionService);
      inOrder
          .verify(injectExecutionService)
          .processInjectExecution(
              eq(inject),
              eq(agent),
              argThat(input -> input == early.getInjectExecutionInput()),
              anySet());
      inOrder
          .verify(injectExecutionService)
          .processInjectExecution(
              eq(inject),
              eq(agent),
              argThat(input -> input == middle.getInjectExecutionInput()),
              anySet());
      inOrder
          .verify(injectExecutionService)
          .processInjectExecution(
              eq(inject),
              eq(agent),
              argThat(input -> input == late.getInjectExecutionInput()),
              anySet());
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
          .processInjectExecution(any(), any(), any(), anySet());

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
    @DisplayName(
        "should not add callback to success list when complete action received for non-PENDING"
            + " inject")
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

      // DataIntegrityViolationException is a general Exception → NOT added to success
      assertTrue(result.isEmpty());
      verify(injectExecutionService, never()).processInjectExecution(any(), any(), any(), anySet());
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
      verify(injectExecutionService).processInjectExecution(eq(inject), eq(agent), any(), anySet());
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
      verify(injectExecutionService).processInjectExecution(eq(inject), eq(agent), any(), anySet());
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
    @DisplayName("should call processInjectExecution with correct arguments")
    void shouldCallProcessInjectExecutionWithCorrectArgs() {
      Inject inject = createInjectWithPendingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);
      Set<OutputParser> outputParsers = Set.of();

      when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));
      when(structuredOutputUtils.extractOutputParsers(inject)).thenReturn(outputParsers);

      InjectExecutionCallback callback =
          createCallback(INJECT_ID, AGENT_ID, InjectExecutionAction.command_execution, 1000L);

      List<InjectExecutionCallback> result =
          service.handleInjectExecutionCallback(List.of(callback));

      assertEquals(1, result.size());
      assertSame(callback, result.get(0));
      verify(injectExecutionService)
          .processInjectExecution(
              eq(inject), eq(agent), eq(callback.getInjectExecutionInput()), eq(outputParsers));
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
      verify(injectExecutionService).processInjectExecution(eq(inject), isNull(), any(), anySet());
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
      verify(injectExecutionService, never()).processInjectExecution(any(), any(), any(), anySet());
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
          .processInjectExecution(same(inject), eq(agent1), any(), anySet());
      verify(injectExecutionService)
          .processInjectExecution(same(inject), eq(agent2), any(), anySet());
    }
  }
}
