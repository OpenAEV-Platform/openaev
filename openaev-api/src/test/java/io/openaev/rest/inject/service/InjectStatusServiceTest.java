package io.openaev.rest.inject.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.database.model.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InjectStatusServiceTest {

  @InjectMocks private InjectStatusService injectStatusService;

  @Test
  public void givenExecutionTraceIsCompleteError_whenComputing_thenTrace_isCompleteError() {
    // given
    Agent agent = new Agent();
    ExecutionTrace executionTrace = new ExecutionTrace();
    executionTrace.setStatus(ExecutionTraceStatus.ERROR);
    executionTrace.setAction(ExecutionTraceAction.COMPLETE);
    executionTrace.setAgent(agent);

    // traces:
    ExecutionTrace executionTrace1 = new ExecutionTrace();
    executionTrace1.setStatus(ExecutionTraceStatus.INFO);
    executionTrace1.setAction(ExecutionTraceAction.START);
    executionTrace1.setAgent(agent);
    ExecutionTrace executionTrace2 = new ExecutionTrace();
    executionTrace2.setStatus(ExecutionTraceStatus.SUCCESS);
    executionTrace2.setAction(ExecutionTraceAction.EXECUTION);
    executionTrace2.setAgent(agent);
    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setTraces(List.of(executionTrace1, executionTrace2));

    // when
    injectStatusService.computeExecutionTraceStatusIfNeeded(injectStatus, executionTrace, agent);

    // then
    assertEquals(ExecutionTraceStatus.ERROR, executionTrace.getStatus());
    assertEquals(ExecutionTraceAction.COMPLETE, executionTrace.getAction());
  }

  @Test
  public void
      givenExecutionTraceIsCompleteInfoAndNoError_whenComputing_thenTrace_isCompleteSuccess() {
    // given
    Agent agent = new Agent();
    ExecutionTrace executionTrace = new ExecutionTrace();

    // traces:
    executionTrace.setStatus(ExecutionTraceStatus.INFO);
    executionTrace.setAction(ExecutionTraceAction.START);
    executionTrace.setAgent(agent);
    ExecutionTrace executionTrace2 = new ExecutionTrace();
    executionTrace2.setStatus(ExecutionTraceStatus.SUCCESS);
    executionTrace2.setAction(ExecutionTraceAction.EXECUTION);
    executionTrace2.setAgent(agent);
    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setTraces(List.of(executionTrace, executionTrace2));

    // when
    injectStatusService.computeExecutionTraceStatusIfNeeded(injectStatus, executionTrace, agent);

    // then
    assertEquals(ExecutionTraceStatus.INFO, executionTrace.getStatus());
    assertEquals(ExecutionTraceAction.START, executionTrace.getAction());
    assertEquals(ExecutionTraceStatus.SUCCESS, executionTrace2.getStatus());
    assertEquals(ExecutionTraceAction.EXECUTION, executionTrace2.getAction());
  }

  @Test
  public void givenExecutionTraceIsCompleteInfoWithError_whenComputing_thenTrace_isCompleteError() {
    // given
    Agent agent = new Agent();

    // traces:
    ExecutionTrace executionTrace1 = new ExecutionTrace();
    executionTrace1.setStatus(ExecutionTraceStatus.INFO);
    executionTrace1.setAction(ExecutionTraceAction.START);
    executionTrace1.setAgent(agent);
    ExecutionTrace executionTrace2 = new ExecutionTrace();
    executionTrace2.setStatus(ExecutionTraceStatus.ERROR);
    executionTrace2.setAction(ExecutionTraceAction.EXECUTION);
    executionTrace2.setAgent(agent);
    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setTraces(List.of(executionTrace1, executionTrace2));

    // when
    injectStatusService.computeExecutionTraceStatusIfNeeded(injectStatus, executionTrace1, agent);

    // then
    assertEquals(ExecutionTraceStatus.INFO, executionTrace1.getStatus());
    assertEquals(ExecutionTraceAction.START, executionTrace1.getAction());
    assertEquals(ExecutionTraceStatus.ERROR, executionTrace2.getStatus());
    assertEquals(ExecutionTraceAction.EXECUTION, executionTrace2.getAction());
  }

  @Test
  public void givenExecutionTraceWithUnsupportedStatus_whenComputing_thenThrowException() {
    // given
    Agent agent = new Agent();

    ExecutionTrace executionTrace = new ExecutionTrace();
    executionTrace.setStatus(ExecutionTraceStatus.INFO); // unsupported status
    executionTrace.setAction(ExecutionTraceAction.COMPLETE);
    executionTrace.setAgent(agent);

    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setTraces(List.of(executionTrace));

    // when / then
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                injectStatusService.computeExecutionTraceStatusIfNeeded(
                    injectStatus, executionTrace, agent));

    assertEquals("Invalid execution trace status: INFO", exception.getMessage());
  }
}
