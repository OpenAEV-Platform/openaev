package io.openaev.utils;

import static io.openaev.database.model.ExecutionTraceStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InjectStatusUtils Tests")
class InjectStatusUtilsTest {

  private static ExecutionTrace buildCompleteTrace(ExecutionTraceStatus status) {
    return new ExecutionTrace(
        null, status, null, "test", ExecutionTraceAction.COMPLETE, null, null);
  }

  @Nested
  @DisplayName("Method: computeStatus")
  class ComputeStatus {

    @Test
    @DisplayName(
        "Given a successful trace and a not targeted trace should return EXECUTED, not PARTIAL")
    void given_successAndClearanceRestrictedTraces_should_returnExecuted() {
      // -- ARRANGE --
      List<ExecutionTrace> traces =
          List.of(buildCompleteTrace(EXECUTED), buildCompleteTrace(ASSET_CLEARANCE_RESTRICTED));

      // -- ACT --
      ExecutionStatus result = InjectStatusUtils.computeStatus(traces);

      // -- ASSERT --
      assertThat(result).isEqualTo(ExecutionStatus.EXECUTED);
    }

    @Test
    @DisplayName("Given only not targeted traces should return ERROR as nothing was executed")
    void given_onlyClearanceRestrictedTraces_should_returnError() {
      // -- ARRANGE --
      List<ExecutionTrace> traces =
          List.of(
              buildCompleteTrace(ASSET_CLEARANCE_RESTRICTED),
              buildCompleteTrace(ASSET_CLEARANCE_RESTRICTED));

      // -- ACT --
      ExecutionStatus result = InjectStatusUtils.computeStatus(traces);

      // -- ASSERT --
      assertThat(result).isEqualTo(ExecutionStatus.ERROR);
    }
  }
}
