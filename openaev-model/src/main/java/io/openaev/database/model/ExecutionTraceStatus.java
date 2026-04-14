package io.openaev.database.model;

import java.util.Set;

public enum ExecutionTraceStatus {

  // -- Success status --
  SUCCESS,
  WARNING,
  ACCESS_DENIED,

  // -- Error status --
  ERROR,
  COMMAND_NOT_FOUND,
  COMMAND_CANNOT_BE_EXECUTED,
  INVALID_USAGE,
  TIMEOUT,
  INTERRUPTED,

  // -- Not counted (ignored in status computation) --
  ASSET_AGENTLESS,
  AGENT_INACTIVE,
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
          INVALID_USAGE,
          TIMEOUT,
          INTERRUPTED,
          // @deprecated — rerouted to error
          PARTIAL,
          MAYBE_PREVENTED,
          MAYBE_PARTIAL_PREVENTED);

  /** Trace statuses that indicate a successful execution. */
  public static final Set<ExecutionTraceStatus> SUCCESS_STATUSES =
      Set.of(SUCCESS, WARNING, ACCESS_DENIED);

  public boolean isError() {
    return ERROR_STATUSES.contains(this);
  }

  public boolean isSuccess() {
    return SUCCESS_STATUSES.contains(this);
  }
}
