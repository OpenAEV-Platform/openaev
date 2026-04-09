package io.openaev.utils;

import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.rest.inject.form.InjectExecutionAction;

/**
 * Utility class for execution trace conversions.
 *
 * <p>Provides methods for converting between different execution status and action enumerations
 * used in inject execution tracking.
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.openaev.database.model.ExecutionTraceStatus
 * @see io.openaev.database.model.ExecutionTraceAction
 */
public class ExecutionTraceUtils {

  private ExecutionTraceUtils() {}

  /**
   * Converts an inject execution action to its corresponding trace action.
   *
   * @param action the inject execution action to convert
   * @return the corresponding execution trace action, defaults to EXECUTION for unmapped actions
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
