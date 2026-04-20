package io.openaev.database.model;

public enum ExecutionStatus {
  // Inject Status
  SUCCESS,
  PARTIAL,
  ERROR,

  // -- Deprecated (kept for backward compatibility with existing DB data) --
  @Deprecated
  MAYBE_PREVENTED,
  @Deprecated
  MAYBE_PARTIAL_PREVENTED,

  // Inject Execution Progress
  DRAFT,
  QUEUING,
  EXECUTING,
  PENDING,
}
