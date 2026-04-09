package io.openaev.database.model;

public enum ExecutionStatus {
  // Inject Status
  SUCCESS,
  ERROR,

  /**
   * @deprecated Kept for backward compatibility with existing DB data. Never set this value in new
   *     code. Rerouted to {@link #ERROR} at computation time.
   */
  @Deprecated
  MAYBE_PREVENTED,

  PARTIAL,

  /**
   * @deprecated Kept for backward compatibility with existing DB data. Never set this value in new
   *     code. Rerouted to {@link #ERROR} at computation time.
   */
  @Deprecated
  MAYBE_PARTIAL_PREVENTED,

  // Inject Execution Progress
  DRAFT,
  QUEUING,
  EXECUTING,
  PENDING,
}
