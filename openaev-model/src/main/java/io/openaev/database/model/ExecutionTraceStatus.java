package io.openaev.database.model;

import java.util.Locale;
import java.util.Set;

public enum ExecutionTraceStatus {

  // -- Success status --
  EXECUTED,
  EXECUTED_WITH_CLEANUP_FAILURE,
  EXECUTED_WITH_CLEANUP_FAIL,
  WARNING,
  ACCESS_DENIED,

  // -- Error status --
  ERROR,
  COMMAND_NOT_FOUND,
  COMMAND_CANNOT_BE_EXECUTED,
  PREREQUISITE_FAILED,
  INVALID_USAGE,
  TIMEOUT,
  INTERRUPTED,

  // -- Not counted (ignored in status computation) --
  ASSET_AGENTLESS,
  AGENT_INACTIVE,
  AGENT_OVERLOADED,
  INFO,

  // -- Deprecated (kept for backward compatibility with existing DB data) --

  @Deprecated
  PARTIAL,
  @Deprecated
  MAYBE_PREVENTED,
  @Deprecated
  MAYBE_PARTIAL_PREVENTED;

  /** Trace statuses that indicate a failed execution. */
  public static final Set<ExecutionTraceStatus> ERROR_STATUSES =
      Set.of(
          ERROR,
          COMMAND_NOT_FOUND,
          COMMAND_CANNOT_BE_EXECUTED,
          PREREQUISITE_FAILED,
          INVALID_USAGE,
          TIMEOUT,
          INTERRUPTED,
          // @deprecated — rerouted to error
          PARTIAL,
          MAYBE_PREVENTED,
          MAYBE_PARTIAL_PREVENTED);

  /** Trace statuses that indicate a successful execution. */
  public static final Set<ExecutionTraceStatus> SUCCESS_STATUSES =
      Set.of(
          EXECUTED,
          EXECUTED_WITH_CLEANUP_FAILURE,
          EXECUTED_WITH_CLEANUP_FAIL,
          WARNING,
          ACCESS_DENIED);

  public static ExecutionTraceStatus fromName(String status) {
    String normalized = normalize(status);
    return switch (normalized) {
      case "SUCCESS" -> EXECUTED;
      case "SUCCESS_WITH_CLEANUP_FAIL",
              "SUCCESS_WITH_CLEANUP_FAILURE",
              "EXECUTED_WITH_CLEANUP_FAILURE",
              "EXECUTED_WITH_CLEANUP_FAIL" ->
          EXECUTED_WITH_CLEANUP_FAILURE;
      default -> ExecutionTraceStatus.valueOf(normalized);
    };
  }

  private static String normalize(String status) {
    if (status == null || status.isBlank()) {
      throw new IllegalArgumentException("Execution trace status must not be null or blank");
    }
    return status.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
  }

  public boolean isError() {
    return ERROR_STATUSES.contains(this);
  }

  public boolean isSuccess() {
    return SUCCESS_STATUSES.contains(this);
  }
}
