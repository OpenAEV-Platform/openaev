package io.openaev.database.model;

import java.util.Set;

public enum ExecutionTraceStatus {

  // -- Success --
  SUCCESS,
  WARNING,
  ACCESS_DENIED,

  // -- Error --
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

  /**
   * @deprecated A trace-level status should not be PARTIAL. Rerouted to {@link #ERROR} at
   *     computation time. PARTIAL only exists at inject level ({@link ExecutionStatus#PARTIAL}).
   */
  @Deprecated
  PARTIAL,

  /**
   * @deprecated Rerouted to {@link #ERROR} at computation time. The implant may still send this
   *     value, but it is always reclassified by {@code InjectStatusService}.
   */
  @Deprecated
  MAYBE_PREVENTED,

  /**
   * @deprecated Rerouted to {@link #ERROR} at computation time. Never set this value in new code.
   */
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
