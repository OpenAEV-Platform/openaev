package io.openaev.utils;

import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceStatus;
import java.util.List;

public class InjectStatusUtils {

  private InjectStatusUtils() {}

  /**
   * Compute the global execution status from a list of COMPLETE traces (one per agent).
   *
   * <p>Filters out AGENT_INACTIVE traces. If no active traces remain, returns ERROR.
   */
  public static ExecutionStatus computeStatus(List<ExecutionTrace> traces) {
    List<ExecutionTrace> activeTraces =
        traces.stream()
            .filter(t -> !ExecutionTraceStatus.AGENT_INACTIVE.equals(t.getStatus()))
            .toList();

    if (activeTraces.isEmpty()) {
      return ExecutionStatus.ERROR;
    }

    boolean hasError = false;
    boolean hasSuccess = false;

    for (ExecutionTrace trace : activeTraces) {
      ExecutionTraceStatus status = trace.getStatus();
      if (status.isError()) {
        hasError = true;
      } else if (status.isSuccess()) {
        hasSuccess = true;
      }
    }

    if (hasError && !hasSuccess) {
      return ExecutionStatus.ERROR;
    }
    if (hasSuccess && !hasError) {
      return ExecutionStatus.SUCCESS;
    }
    return ExecutionStatus.PARTIAL;
  }
}
