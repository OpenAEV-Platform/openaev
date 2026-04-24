package io.openaev.utils;

import static io.openaev.database.model.ExecutionTraceAction.*;
import static io.openaev.database.model.ExecutionTraceStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExecutionTraceUtils Tests")
class ExecutionTraceUtilsTest {

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
}
