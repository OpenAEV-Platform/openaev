package io.openaev.database.model;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.Set;

public enum ExecutionStatus {
  // Inject Status
  EXECUTED,
  PARTIAL,
  ERROR,

  // -- Deprecated (kept for backward compatibility with existing DB data) --
  // @Hidden keeps them out of the filter value pickers (SchemaUtils.getEnumNames)
  @Hidden
  @Deprecated
  MAYBE_PREVENTED,
  @Hidden
  @Deprecated
  MAYBE_PARTIAL_PREVENTED,

  // Inject Execution Progress
  DRAFT,
  QUEUING,
  EXECUTING,
  PENDING,
  ;

  /** Inject statuses considered successful at aggregate level. */
  public static final Set<ExecutionStatus> SUCCESS_STATUSES = Set.of(EXECUTED);

  /** Inject statuses considered error/failed at aggregate level. */
  public static final Set<ExecutionStatus> ERROR_STATUSES =
      Set.of(ERROR, PARTIAL, MAYBE_PREVENTED, MAYBE_PARTIAL_PREVENTED);

  /** Legacy-aware parser: keeps backward compatibility for historical SUCCESS value. */
  public static ExecutionStatus fromName(String status) {
    if ("SUCCESS".equalsIgnoreCase(status)) {
      return EXECUTED;
    }
    return ExecutionStatus.valueOf(status);
  }

  public boolean isError() {
    return ERROR_STATUSES.contains(this);
  }

  public boolean isSuccess() {
    return SUCCESS_STATUSES.contains(this);
  }
}
