package io.openaev.rest.inject.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.InjectStatusRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InjectStatusServiceTest {

  @Mock private InjectStatusRepository injectStatusRepository;

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

  /* ============================================================
   * Delete inject statuses for a list of injects
   * ============================================================ */
  @Nested
  @DisplayName("deleteAllInjectStatusByInjects")
  class DeleteAllInjectStatusByInjectsTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("injectsProvider")
    void deleteAllInjectStatusByInjects_parameterized(
        String description, List<Inject> injects, List<String> expectedIds) {
      // Act
      injectStatusService.deleteAllInjectStatusByInjects(injects);

      // Assert
      ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
      verify(injectStatusRepository).deleteAllById(captor.capture());

      List<String> capturedIds = captor.getValue();
      assertEquals(
          expectedIds.size(), capturedIds.size(), "Size mismatch for scenario: " + description);
      assertTrue(capturedIds.containsAll(expectedIds), "IDs mismatch for scenario: " + description);
    }

    private static Stream<Arguments> injectsProvider() {
      return Stream.of(
          Arguments.of(
              "All injects have status",
              List.of(injectWithId("1"), injectWithId("2")),
              List.of("1", "2")),
          Arguments.of(
              "Some injects missing status",
              List.of(injectWithId("1"), injectWithEmptyStatus()),
              List.of("1", "")),
          Arguments.of(
              "All injects missing status",
              List.of(injectWithEmptyStatus(), injectWithEmptyStatus()),
              List.of("", "")),
          Arguments.of("Empty list of injects", List.of(), List.of()));
    }

    private static Inject injectWithId(String id) {
      InjectStatus status = mock(InjectStatus.class);
      when(status.getId()).thenReturn(id);

      Inject inject = mock(Inject.class);
      when(inject.getStatus()).thenReturn(Optional.of(status));
      return inject;
    }

    private static Inject injectWithEmptyStatus() {
      Inject inject = mock(Inject.class);
      when(inject.getStatus()).thenReturn(Optional.empty());
      return inject;
    }
  }
}
