package io.openaev.aop.audit_log;

/**
 * Indicates the origin context of an audit event.
 *
 * <p>Determines whether HTTP user/session metadata should be populated on the log document.
 */
public enum AuditEventOrigin {
  /** HTTP request context available (controllers, filters). */
  REQUEST,
  /** Scheduled jobs, message consumers, startup tasks. */
  SCHEDULED
}
