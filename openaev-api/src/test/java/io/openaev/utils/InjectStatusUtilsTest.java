package io.openaev.utils;

import static io.openaev.database.model.ExecutionTraceStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.utils.InjectStatusUtils.CompleteTrace;
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

  @Nested
  @DisplayName("Method: visibleStatus")
  class VisibleStatus {

    private static final String RED_AGENT = "red-agent";
    private static final String GREEN_AGENT = "green-agent";

    private static CompleteTrace of(String agentId, ExecutionTraceStatus status) {
      return new CompleteTrace(status, agentId);
    }

    @Test
    @DisplayName("Given every agent hidden from the reader should return NOT_VISIBLE")
    void given_everyAgentHidden_should_returnNotVisible() {
      // -- ACT --
      ExecutionStatus result =
          InjectStatusUtils.visibleStatus(ExecutionStatus.EXECUTED, 2, List.of());

      // -- ASSERT --
      assertThat(result).isEqualTo(ExecutionStatus.NOT_VISIBLE);
    }

    @Test
    @DisplayName("Given only the failed agent visible should return ERROR")
    void given_onlyFailedAgentVisible_should_returnError() {
      // -- ACT --
      ExecutionStatus result =
          InjectStatusUtils.visibleStatus(
              ExecutionStatus.PARTIAL, 2, List.of(of(GREEN_AGENT, ERROR)));

      // -- ASSERT --
      assertThat(result).isEqualTo(ExecutionStatus.ERROR);
    }

    @Test
    @DisplayName("Given every agent visible should return the stored status")
    void given_everyAgentVisible_should_returnStored() {
      // -- ACT --
      ExecutionStatus result =
          InjectStatusUtils.visibleStatus(
              ExecutionStatus.PARTIAL, 2, List.of(of(RED_AGENT, EXECUTED), of(GREEN_AGENT, ERROR)));

      // -- ASSERT --
      assertThat(result).isEqualTo(ExecutionStatus.PARTIAL);
    }

    @Test
    @DisplayName("Given a status set by hand, with no agent dispatched, should return it")
    void given_noAgentDispatched_should_returnStored() {
      // -- ACT --
      ExecutionStatus result =
          InjectStatusUtils.visibleStatus(ExecutionStatus.EXECUTED, null, List.of());

      // -- ASSERT --
      // No trace for anyone: hiding nothing, the reader sees what the admin sees
      assertThat(result).isEqualTo(ExecutionStatus.EXECUTED);
    }

    @Test
    @DisplayName("Given an inject closed by the platform should return the stored status")
    void given_closedByPlatform_should_returnStored() {
      // -- ACT --
      ExecutionStatus result =
          InjectStatusUtils.visibleStatus(
              ExecutionStatus.ERROR, 2, List.of(of(null, INTERRUPTED), of(GREEN_AGENT, EXECUTED)));

      // -- ASSERT --
      assertThat(result).isEqualTo(ExecutionStatus.ERROR);
    }

    @Test
    @DisplayName("Given only agents that were not executed visible should return NOT_VISIBLE")
    void given_onlyNotCountedAgentVisible_should_returnNotVisible() {
      // -- ACT --
      ExecutionStatus result =
          InjectStatusUtils.visibleStatus(
              ExecutionStatus.EXECUTED, 2, List.of(of(GREEN_AGENT, ASSET_CLEARANCE_RESTRICTED)));

      // -- ASSERT --
      assertThat(result).isEqualTo(ExecutionStatus.NOT_VISIBLE);
    }

    @Test
    @DisplayName("Given an inject still in flight should keep its lifecycle status")
    void given_pending_should_keepStored() {
      // -- ACT --
      ExecutionStatus result =
          InjectStatusUtils.visibleStatus(ExecutionStatus.PENDING, 2, List.of());

      // -- ASSERT --
      assertThat(result).isEqualTo(ExecutionStatus.PENDING);
    }
  }
}
