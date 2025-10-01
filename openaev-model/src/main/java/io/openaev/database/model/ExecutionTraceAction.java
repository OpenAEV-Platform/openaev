package io.openaev.database.model;

public enum ExecutionTraceAction {
  START,
  PREREQUISITE_CHECK,
  PREREQUISITE_EXECUTION,
  EXECUTION,
  CLEANUP_EXECUTION,
  COMPLETE, // when inject of one asset is finish, or of one user
}
