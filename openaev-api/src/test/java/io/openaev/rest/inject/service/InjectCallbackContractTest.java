package io.openaev.rest.inject.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.helper.queue.BatchQueueService;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.service.InjectExpectationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Contract tests that verify the sync path ({@link
 * InjectExecutionService#handleInjectExecutionCallback}) and the async batch path ({@link
 * BatchingInjectStatusService#handleInjectExecutionCallback}) apply the same business rules.
 *
 * <p>Each test runs the same assertion against both paths using a functional interface that
 * abstracts the invocation.
 */
@ExtendWith(MockitoExtension.class)
// this is a contract test only where each parameterization run different code paths, lenient mode
// is OK in this case.
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Inject Callback Contract Tests")
class InjectCallbackContractTest {

  @Mock private InjectRepository injectRepository;
  @Mock private AgentRepository agentRepository;
  @Mock private InjectExpectationService injectExpectationService;
  @Mock private InjectStatusService injectStatusService;
  @Mock private InjectService injectService;
  @Mock private AgentExecutionProcessingHandler agentExecutionProcessingHandler;
  @Mock private InjectorExecutionProcessingHandler injectorExecutionProcessingHandler;
  @Mock private StructuredOutputUtils structuredOutputUtils;
  @Mock private BatchQueueService<InjectExecutionCallback> injectTraceQueueService;

  private InjectExecutionService injectExecutionService;
  private BatchingInjectStatusService batchingService;

  @FunctionalInterface
  interface CallbackInvoker {
    void invoke(String injectId, String agentId, InjectExecutionInput input) throws Exception;
  }

  @BeforeEach
  void setUp() throws Exception {
    lenient().when(injectService.getValueTargetedAssetMap(any())).thenReturn(Map.of());
    lenient()
        .doReturn(Optional.empty())
        .when(agentExecutionProcessingHandler)
        .processContext(any());
    lenient()
        .doReturn(Optional.empty())
        .when(injectorExecutionProcessingHandler)
        .processContext(any());

    // Spy: real object with mock dependencies, so we can verify method calls
    injectExecutionService =
        spy(
            new InjectExecutionService(
                injectRepository,
                injectExpectationService,
                agentRepository,
                injectStatusService,
                injectService,
                agentExecutionProcessingHandler,
                injectorExecutionProcessingHandler));

    // Can't use @InjectMocks: batchingService needs the spy, not a plain mock
    batchingService =
        new BatchingInjectStatusService(
            injectRepository, agentRepository, structuredOutputUtils, injectExecutionService);
    batchingService.setInjectTraceQueueService(injectTraceQueueService);
  }

  private CallbackInvoker syncInvoker() {
    return (injectId, agentId, input) ->
        injectExecutionService.handleInjectExecutionCallback(injectId, agentId, input);
  }

  private CallbackInvoker asyncInvoker() {
    return (injectId, agentId, input) -> {
      InjectExecutionCallback callback =
          InjectExecutionCallback.builder()
              .injectId(injectId)
              .agentId(agentId)
              .injectExecutionInput(input)
              .emissionDate(System.currentTimeMillis())
              .build();
      batchingService.handleInjectExecutionCallback(List.of(callback));
    };
  }

  static Stream<Arguments> bothPaths() {
    return Stream.of(
        Arguments.of(Named.of("sync", "sync")), Arguments.of(Named.of("async", "async")));
  }

  private CallbackInvoker invokerFor(String path) {
    return "sync".equals(path) ? syncInvoker() : asyncInvoker();
  }

  private Inject createInjectWithStatus(String injectId, ExecutionStatus executionStatus) {
    Inject inject = new Inject();
    inject.setId(injectId);
    InjectStatus status = new InjectStatus();
    status.setName(executionStatus);
    inject.setStatus(status);
    return inject;
  }

  private Agent createAgent(String agentId) {
    Agent agent = new Agent();
    agent.setId(agentId);
    return agent;
  }

  private InjectExecutionInput createInput(InjectExecutionAction action) {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setAction(action);
    input.setMessage("test");
    input.setStatus("SUCCESS");
    return input;
  }

  // ========================================================================
  // Contract: neither path processes a complete action on a non-PENDING inject.
  //
  // Note: the two paths diverge on *how* they reject it — the sync path still
  // throws DataIntegrityViolationException, while the async batch path silently
  // queues the callback for later retry (handled by ExecutionTracesBatchRequeueJob).
  // What remains a true contract is that neither path delegates the execution
  // processing in that situation.
  // ========================================================================
  @ParameterizedTest(name = "{0}")
  @MethodSource("bothPaths")
  @DisplayName("should not process complete action for non-PENDING inject")
  void shouldRejectNonPendingCompleteAction(String path) {
    Inject inject = createInjectWithStatus("inject-1", ExecutionStatus.EXECUTING);

    when(injectRepository.findById("inject-1")).thenReturn(Optional.of(inject));
    when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
    when(agentRepository.findAllById(anyList())).thenReturn(List.of());

    InjectExecutionInput input = createInput(InjectExecutionAction.complete);
    CallbackInvoker invoker = invokerFor(path);

    if ("sync".equals(path)) {
      // Sync path still throws DataIntegrityViolationException (not caught internally)
      assertThrows(
          DataIntegrityViolationException.class, () -> invoker.invoke("inject-1", null, input));
    } else {
      // Async batch path does not throw — it queues the callback for retry instead.
      assertDoesNotThrow(() -> invoker.invoke("inject-1", null, input));
    }

    // Neither path should have delegated to execution processing
    verify(injectExecutionService, never()).processInjectExecutionWithAgent(any(), any(), any());
    verify(injectExecutionService, never()).processInjectExecutionWithInjector(any(), any());
  }

  // ========================================================================
  // Contract: both paths handle missing inject gracefully
  // ========================================================================
  @ParameterizedTest(name = "{0}")
  @MethodSource("bothPaths")
  @DisplayName("should handle missing inject gracefully")
  void shouldHandleMissingInjectGracefully(String path) {
    when(injectRepository.findById("missing-inject")).thenReturn(Optional.empty());
    when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of());
    when(agentRepository.findAllById(anyList())).thenReturn(List.of());

    InjectExecutionInput input = createInput(InjectExecutionAction.command_execution);
    CallbackInvoker invoker = invokerFor(path);

    // Both paths should handle the missing inject without uncaught exceptions
    assertDoesNotThrow(() -> invoker.invoke("missing-inject", null, input));

    // Both paths should call handleInjectExecutionError
    verify(injectExecutionService).handleInjectExecutionError(isNull(), any(Exception.class));
  }

  // ========================================================================
  // Contract: both paths handle missing agent gracefully
  // ========================================================================
  @ParameterizedTest(name = "{0}")
  @MethodSource("bothPaths")
  @DisplayName("should handle missing agent gracefully")
  void shouldHandleMissingAgentGracefully(String path) {
    Inject inject = createInjectWithStatus("inject-1", ExecutionStatus.PENDING);

    when(injectRepository.findById("inject-1")).thenReturn(Optional.of(inject));
    when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
    when(agentRepository.findById("missing-agent")).thenReturn(Optional.empty());
    when(agentRepository.findAllById(anyList())).thenReturn(List.of());

    InjectExecutionInput input = createInput(InjectExecutionAction.command_execution);
    CallbackInvoker invoker = invokerFor(path);

    // Both paths should handle the missing agent without uncaught exceptions
    assertDoesNotThrow(() -> invoker.invoke("inject-1", "missing-agent", input));

    // Both paths should call handleInjectExecutionError
    verify(injectExecutionService).handleInjectExecutionError(eq(inject), any(Exception.class));
  }

  // ========================================================================
  // Contract: both paths delegate to processInjectExecution for valid callbacks
  // ========================================================================
  @ParameterizedTest(name = "{0}")
  @MethodSource("bothPaths")
  @DisplayName("should call processInjectExecution for valid callbacks")
  void shouldDelegateToProcessInjectExecution(String path) throws Exception {
    Inject inject = createInjectWithStatus("inject-1", ExecutionStatus.PENDING);
    Agent agent = createAgent("agent-1");

    when(injectRepository.findById("inject-1")).thenReturn(Optional.of(inject));
    when(injectRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of(inject));
    when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
    when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

    InjectExecutionInput input = createInput(InjectExecutionAction.command_execution);
    CallbackInvoker invoker = invokerFor(path);

    invoker.invoke("inject-1", "agent-1", input);

    verify(injectExecutionService)
        .processInjectExecutionWithAgent(eq(inject), eq(agent), eq(input));
  }
}
