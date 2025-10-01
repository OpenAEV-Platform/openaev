package io.openaev.utils.fixtures;

import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import java.util.List;

public class ExecutionTraceFixture {

  public static ExecutionTrace createDefaultExecutionTraceStart() {
    return ExecutionTrace.getNewInfoTrace("Info", ExecutionTraceAction.START);
  }

  public static ExecutionTrace createDefaultExecutionTraceStartWithIdentifiers(
      List<String> identifiers) {
    return ExecutionTrace.getNewInfoTrace("Info", ExecutionTraceAction.START, identifiers);
  }

  public static ExecutionTrace createDefaultExecutionTraceComplete() {
    return ExecutionTrace.getNewSuccessTrace("Success", ExecutionTraceAction.COMPLETE);
  }

  public static ExecutionTrace createDefaultExecutionTraceCompleteWithIdentifiers(
      List<String> identifiers) {
    return ExecutionTrace.getNewSuccessTrace("Success", ExecutionTraceAction.COMPLETE, identifiers);
  }

  public static ExecutionTrace createDefaultExecutionTraceError() {
    return ExecutionTrace.getNewErrorTrace("Error", ExecutionTraceAction.COMPLETE);
  }
}
