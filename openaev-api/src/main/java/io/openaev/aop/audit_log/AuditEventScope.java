package io.openaev.aop.audit_log;

import io.openaev.database.model.Action;
import io.openaev.database.model.EventType;

/** Fine-grained scope within an {@link EventType}. */
public enum AuditEventScope {
  // Authentication
  LOGIN,
  LOGOUT,
  SESSION_EXPIRED,
  UNAUTHORIZED,

  // Mutation (CRUD)
  CREATE,
  UPDATE,
  DELETE,
  DUPLICATE,
  STATUS_CHANGE,

  // Execution (inject lifecycle)
  SCHEDULED_LAUNCH,
  INJECT_STATUS_TRANSITION,
  TARGET_RESOLUTION,
  AGENT_TRACE_STEP,
  COVERAGE_GAP,
  INJECT_FINAL_STATUS,
  EXPECTATION_RESULT,
  INJECT_QUEUED,

  // System
  RETENTION_PURGE,
  JOB_EXECUTION,
  MIGRATION,
  STARTUP;

  /** Maps an {@link Action} to its corresponding {@link AuditEventScope}. */
  public static AuditEventScope from(Action action) {
    return switch (action) {
      case CREATE -> CREATE;
      case WRITE -> UPDATE;
      case DELETE -> DELETE;
      case LAUNCH -> STATUS_CHANGE;
      case DUPLICATE -> DUPLICATE;
      case LOGIN -> LOGIN;
      case LOGOUT -> LOGOUT;
      case UNAUTHORIZED -> UNAUTHORIZED;
      default -> throw new IllegalArgumentException("Unmapped Action: " + action);
    };
  }
}
