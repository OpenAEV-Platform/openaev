package io.openaev.database.model;

public enum ExecutionStatus {
  // Inject Status
  SUCCESS,
  ERROR,
  MAYBE_PREVENTED,
  PARTIAL,
  MAYBE_PARTIAL_PREVENTED,

  // Inject Execution Progress
  DRAFT,
  QUEUING,
  EXECUTING,
  PENDING,
}
