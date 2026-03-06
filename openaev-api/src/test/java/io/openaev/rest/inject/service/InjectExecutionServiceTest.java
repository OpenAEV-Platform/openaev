package io.openaev.rest.inject.service;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Inject;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.service.InjectExpectationService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InjectExecutionServiceTest {

  private InjectExecutionService service;
  private AgentExecutionProcessingHandler agentHandler;
  private InjectorExecutionProcessingHandler injectorHandler;

  @BeforeEach
  void setUp() {
    agentHandler = mock(AgentExecutionProcessingHandler.class);
    injectorHandler = mock(InjectorExecutionProcessingHandler.class);
    InjectService injectService = mock(InjectService.class);
    InjectStatusService injectStatusService = mock(InjectStatusService.class);
    InjectExpectationService injectExpectationService = mock(InjectExpectationService.class);
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
}
