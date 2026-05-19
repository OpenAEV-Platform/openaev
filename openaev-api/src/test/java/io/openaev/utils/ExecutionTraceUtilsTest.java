package io.openaev.utils;

import static io.openaev.database.model.ExecutionTraceAction.*;
import static io.openaev.database.model.ExecutionTraceStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.rest.inject.service.InjectStatusService;
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

@DisplayName("ExecutionTraceUtils Tests")
@ExtendWith(MockitoExtension.class)
class ExecutionTraceUtilsTest {
  @Mock private InjectStatusRepository injectStatusRepository;
  @InjectMocks private InjectStatusService injectStatusService;

  private static ExecutionTrace buildTrace(
      ExecutionTraceStatus status, ExecutionTraceAction action) {
    return new ExecutionTrace(null, status, null, "test", action, null, null);
  }

  @Nested
  @DisplayName("Method: computeAgentTraceStatus")
  class ComputeAgentTraceStatus {

    @Test
    @DisplayName("Given prerequisite execution error should return PREREQUISITE_FAILED")
    void given_prerequisite_execution_error_should_return_prerequisite_failed() {
      // -- ARRANGE --
      List<ExecutionTrace> traces = List.of(buildTrace(COMMAND_NOT_FOUND, PREREQUISITE_EXECUTION));

      // -- ACT --
      ExecutionTraceStatus result = ExecutionTraceUtils.computeAgentTraceStatus(traces);

      // -- ASSERT --
      assertThat(result).isEqualTo(PREREQUISITE_FAILED);
    }

    @Test
    @DisplayName("Given single execution error should return that error status")
    void given_single_execution_error_should_return_that_error_status() {
      // -- ARRANGE --
      List<ExecutionTrace> traces = List.of(buildTrace(COMMAND_NOT_FOUND, EXECUTION));

      // -- ACT --
      ExecutionTraceStatus result = ExecutionTraceUtils.computeAgentTraceStatus(traces);

      // -- ASSERT --
      assertThat(result).isEqualTo(COMMAND_NOT_FOUND);
    }

    @Test
    @DisplayName("Given multiple different execution errors should return ERROR")
    void given_multiple_different_execution_errors_should_return_error() {
      // -- ARRANGE --
      List<ExecutionTrace> traces =
          List.of(buildTrace(COMMAND_NOT_FOUND, EXECUTION), buildTrace(TIMEOUT, EXECUTION));

      // -- ACT --
      ExecutionTraceStatus result = ExecutionTraceUtils.computeAgentTraceStatus(traces);

      // -- ASSERT --
      assertThat(result).isEqualTo(ERROR);
    }

    @Test
    @DisplayName(
        "Given execution success and cleanup error should return SUCCESS_WITH_CLEANUP_FAIL")
    void given_execution_success_and_cleanup_error_should_return_success_with_cleanup_fail() {
      // -- ARRANGE --
      List<ExecutionTrace> traces =
          List.of(buildTrace(SUCCESS, EXECUTION), buildTrace(ERROR, CLEANUP_EXECUTION));

      // -- ACT --
      ExecutionTraceStatus result = ExecutionTraceUtils.computeAgentTraceStatus(traces);

      // -- ASSERT --
      assertThat(result).isEqualTo(SUCCESS_WITH_CLEANUP_FAIL);
    }

    @Test
    @DisplayName("Given all success should return SUCCESS")
    void given_all_success_should_return_success() {
      // -- ARRANGE --
      List<ExecutionTrace> traces = List.of(buildTrace(SUCCESS, EXECUTION));

      // -- ACT --
      ExecutionTraceStatus result = ExecutionTraceUtils.computeAgentTraceStatus(traces);

      // -- ASSERT --
      assertThat(result).isEqualTo(SUCCESS);
    }

    @Test
    @DisplayName("Given execution error should take priority over prerequisite failure")
    void given_execution_error_should_take_priority_over_prerequisite_failure() {
      // -- ARRANGE --
      List<ExecutionTrace> traces =
          List.of(buildTrace(ERROR, PREREQUISITE_CHECK), buildTrace(COMMAND_NOT_FOUND, EXECUTION));

      // -- ACT --
      ExecutionTraceStatus result = ExecutionTraceUtils.computeAgentTraceStatus(traces);

      // -- ASSERT --
      assertThat(result).isEqualTo(COMMAND_NOT_FOUND);
    }
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
      verify(injectStatusRepository).deleteAllByIds(captor.capture());

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
              List.of("1")),
          Arguments.of(
              "All injects missing status",
              List.of(injectWithEmptyStatus(), injectWithEmptyStatus()),
              List.of()),
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
