package io.openbas.utils;

import io.openbas.database.model.ExecutionStatus;
import io.openbas.database.model.ExecutionTraceAction;
import io.openbas.database.model.ExecutionTraceStatus;
import io.openbas.rest.inject.form.InjectExecutionAction;

public class ExecutionTraceUtils {

  private ExecutionTraceUtils() {}

  /**
   * Convert and ExecutionStatus into an ExecutionTraceStatus
   *
   * @param status the status to convert
   * @return the corresponding ExecutionTraceStatus
   */
  public static ExecutionTraceStatus convertExecutionStatus(ExecutionStatus status) {
    return switch (status) {
      case SUCCESS -> ExecutionTraceStatus.SUCCESS;
      case ERROR -> ExecutionTraceStatus.ERROR;
      case MAYBE_PREVENTED -> ExecutionTraceStatus.MAYBE_PREVENTED;
      case PARTIAL -> ExecutionTraceStatus.PARTIAL;
      case MAYBE_PARTIAL_PREVENTED -> ExecutionTraceStatus.MAYBE_PARTIAL_PREVENTED;
      default -> null;
    };
  }

  /**
   * Convert InjectExecutionAction into ExecutionTraceAction
   *
   * @param action the InjectExecutionAction
   * @return the ExecutionTraceAction
   */
  public static ExecutionTraceAction convertExecutionAction(InjectExecutionAction action) {
    return switch (action) {
      case prerequisite_check -> ExecutionTraceAction.PREREQUISITE_CHECK;
      case prerequisite_execution -> ExecutionTraceAction.PREREQUISITE_EXECUTION;
      case cleanup_execution -> ExecutionTraceAction.CLEANUP_EXECUTION;
      case complete -> ExecutionTraceAction.COMPLETE;
      default -> ExecutionTraceAction.EXECUTION;
    };
  }
}
