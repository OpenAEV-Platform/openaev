package io.openaev.rest.inject.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Inject;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.service.InjectExpectationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InjectExecutionServiceTest {

  private InjectExecutionService service;
  private ExecutionProcessingHandler handler;

  @BeforeEach
  void setUp() {
    handler = mock(ExecutionProcessingHandler.class);
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
            List.of(handler));
  }

  @Test
  @DisplayName("Should resolve handler for agent context")
  void shouldResolveExecutionContextForAgentContext() {
    ExecutionProcessingContext agentContext =
        new ExecutionProcessingContext(
            mock(Inject.class), mock(Agent.class), mock(InjectExecutionInput.class), Map.of());
    when(handler.supports(agentContext)).thenReturn(true);
    ExecutionProcessingHandler resolved = service.resolveExecutionContext(agentContext);
    assertEquals(handler, resolved);
  }

  @Test
  @DisplayName("Should resolve handler for injector context")
  void shouldResolveExecutionContextForInjectorContext() {
    ExecutionProcessingContext injectorContext =
        new ExecutionProcessingContext(
            mock(Inject.class), null, mock(InjectExecutionInput.class), Map.of());
    when(handler.supports(injectorContext)).thenReturn(true);
    ExecutionProcessingHandler resolved = service.resolveExecutionContext(injectorContext);
    assertEquals(handler, resolved);
  }

  @Test
  @DisplayName("Should call processContext on handler in processInjectExecution")
  void shouldCallProcessContextOnHandlerInProcessInjectExecution() throws Exception {
    Inject inject = mock(Inject.class);
    Agent agent = mock(Agent.class);

    InjectExecutionInput input = new InjectExecutionInput();
    String logMessage =
        "{\"stdout\":\"[CVE-2025-25241] [http] [critical] http://seen-ip-endpoint/\\n[CVE-2025-25002] [http] [critical] http://seen-ip-endpoint/\\n\"}";
    input.setMessage(logMessage);
    input.setAction(InjectExecutionAction.command_execution);
    input.setStatus("SUCCESS");

    when(handler.supports(any())).thenReturn(true);
    when(handler.processContext(any())).thenReturn(Optional.of(mock(ObjectNode.class)));
    InjectExecutionService spyService = spy(service);
    doReturn(handler).when(spyService).resolveExecutionContext(any());
    spyService.processInjectExecution(inject, agent, input);
    verify(handler).processContext(any());
  }

  @Test
  @DisplayName("Should throw exception if no handler supports context")
  void shouldThrowExceptionIfNoHandlerSupportsContext() {
    ExecutionProcessingHandler nonSupportingHandler = mock(ExecutionProcessingHandler.class);
    ExecutionProcessingContext context =
        new ExecutionProcessingContext(
            mock(Inject.class), null, mock(InjectExecutionInput.class), Map.of());
    when(nonSupportingHandler.supports(context)).thenReturn(false);
    InjectExecutionService serviceWithNonSupportingHandler =
        new InjectExecutionService(null, null, null, null, null, List.of(nonSupportingHandler));
    Exception ex =
        assertThrows(
            IllegalStateException.class,
            () -> serviceWithNonSupportingHandler.resolveExecutionContext(context));
    assertTrue(ex.getMessage().contains("No handler found"));
  }
}
