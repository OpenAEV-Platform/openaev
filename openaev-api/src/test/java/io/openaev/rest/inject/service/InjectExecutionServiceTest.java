package io.openaev.rest.inject.service;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Agent;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.service.InjectExpectationService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InjectExecutionServiceTest {

  private InjectExecutionService service;
  private AgentExecutionProcessingHandler agentHandler;
  private InjectorExecutionProcessingHandler injectorHandler;
  private InjectStatusService injectStatusService;
  private InjectExpectationService injectExpectationService;

  @BeforeEach
  void setUp() {
    agentHandler = mock(AgentExecutionProcessingHandler.class);
    injectorHandler = mock(InjectorExecutionProcessingHandler.class);
    InjectService injectService = mock(InjectService.class);
    injectStatusService = mock(InjectStatusService.class);
    injectExpectationService = mock(InjectExpectationService.class);
    service =
        new InjectExecutionService(
            null,
            injectExpectationService,
            null,
            injectStatusService,
            injectService,
            agentHandler,
            injectorHandler);
  }

  @Test
  @DisplayName(
      "Should call processContext on handler in processInjectExecution when source is an agent")
  void shouldCallProcessContextOnHandlerInProcessInjectExecution() throws Exception {
    Inject inject = mock(Inject.class);
    Agent agent = mock(Agent.class);

    InjectExecutionInput input = new InjectExecutionInput();
    String logMessage =
        "{\"stdout\":\"[CVE-2025-25241] [http] [critical] http://seen-ip-endpoint/\\n[CVE-2025-25002] [http] [critical] http://seen-ip-endpoint/\\n\"}";
    input.setMessage(logMessage);
    input.setAction(InjectExecutionAction.command_execution);
    input.setStatus("SUCCESS");

    when(agentHandler.processContext(any())).thenReturn(Optional.of(mock(ObjectNode.class)));
    when(injectStatusService.updateInjectStatus(any(), any(), any(), any()))
        .thenReturn(ExecutionTrace.getNewSuccessTrace(logMessage, ExecutionTraceAction.EXECUTION));
    InjectExecutionService spyService = spy(service);
    spyService.processInjectExecutionWithAgent(inject, agent, input);
    verify(agentHandler).processContext(any());
  }

  @Test
  @DisplayName(
      "Should call processContext on handler in processInjectExecution when source is an injector")
  void shouldCallProcessContextOnInjectorHandlerInProcessInjectExecution() throws Exception {
    Inject inject = mock(Inject.class);

    InjectExecutionInput input = new InjectExecutionInput();
    String logMessage =
        "{\"stdout\":\"[CVE-2025-25241] [http] [critical] http://seen-ip-endpoint/\\n[CVE-2025-25002] [http] [critical] http://seen-ip-endpoint/\\n\"}";
    input.setMessage(logMessage);
    input.setAction(InjectExecutionAction.command_execution);
    input.setStatus("SUCCESS");

    when(injectorHandler.processContext(any())).thenReturn(Optional.of(mock(ObjectNode.class)));
    InjectExecutionService spyService = spy(service);
    spyService.processInjectExecutionWithInjector(inject, input);
    verify(injectorHandler).processContext(any());
  }

  @Nested
  @DisplayName("End date signature on inject expectations")
  class EndDateSignature {

    private static final String INJECT_ID = "inject-id";
    private static final String AGENT_ID = "agent-id";

    private Inject inject;
    private Agent agent;
    private InjectExecutionInput completeInput;

    @BeforeEach
    void setUp() {
      inject = mock(Inject.class);
      when(inject.getId()).thenReturn(INJECT_ID);
      when(inject.getStatus()).thenReturn(Optional.of(new InjectStatus()));
      agent = mock(Agent.class);
      when(agent.getId()).thenReturn(AGENT_ID);

      completeInput = new InjectExecutionInput();
      completeInput.setMessage("Payload completed");
      completeInput.setAction(InjectExecutionAction.complete);
      completeInput.setStatus("INFO");
      completeInput.setDuration(1000);
    }

    @Test
    @DisplayName("Given a COMPLETE stored as COMPLETE, should add the end date signature")
    void given_completeStoredAsComplete_should_addEndDateSignature() {
      // Arrange
      Instant endDate = Instant.parse("2026-10-27T10:00:00Z");
      when(injectStatusService.updateInjectStatus(inject, agent, completeInput, null))
          .thenReturn(
              ExecutionTrace.getNewInfoTrace("Payload completed", ExecutionTraceAction.COMPLETE));
      when(injectStatusService.getExecutionTimeFromStartTraceTimeAndDurationByAgentId(
              any(), eq(AGENT_ID), eq(1000)))
          .thenReturn(endDate);

      // Act
      service.processInjectExecutionWithAgent(inject, agent, completeInput);

      // Assert
      verify(injectExpectationService)
          .addEndDateSignatureToInjectExpectationsByAgent(INJECT_ID, AGENT_ID, endDate);
    }

    @Test
    @DisplayName(
        "Given a late COMPLETE stored as EXECUTION, should not add the end date signature")
    void given_lateCompleteStoredAsExecution_should_notAddEndDateSignature() {
      // Arrange
      when(injectStatusService.updateInjectStatus(inject, agent, completeInput, null))
          .thenReturn(
              ExecutionTrace.getNewInfoTrace(
                  "Payload completed", ExecutionTraceAction.EXECUTION));

      // Act
      service.processInjectExecutionWithAgent(inject, agent, completeInput);

      // Assert
      verify(injectExpectationService, never())
          .addEndDateSignatureToInjectExpectationsByAgent(any(), any(), any());
    }
  }
}
