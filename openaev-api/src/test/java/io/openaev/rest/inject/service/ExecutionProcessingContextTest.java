package io.openaev.rest.inject.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Inject;
import io.openaev.rest.inject.form.InjectExecutionInput;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutionProcessingContextTest {

  @Test
  @DisplayName("Should identify agent execution context")
  void shouldIdentifyAgentExecutionContext() {
    ExecutionProcessingContext context =
        new ExecutionProcessingContext(
            mock(Inject.class), mock(Agent.class), mock(InjectExecutionInput.class), Map.of());
    assertTrue(context.isAgentExecution());
    assertFalse(context.isInjectorExecution());
  }

  @Test
  @DisplayName("Should identify injector execution context")
  void shouldIdentifyInjectorExecutionContext() {
    ExecutionProcessingContext context =
        new ExecutionProcessingContext(
            mock(Inject.class), null, mock(InjectExecutionInput.class), Map.of());
    assertFalse(context.isAgentExecution());
    assertTrue(context.isInjectorExecution());
  }
}
