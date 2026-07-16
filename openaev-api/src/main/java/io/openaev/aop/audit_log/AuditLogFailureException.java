package io.openaev.aop.audit_log;

/**
 * Thrown when an audit log transport fails and halt-on-failure is enabled. Propagates through the
 * transaction boundary to trigger a rollback, ensuring that no mutation is persisted without its
 * corresponding audit trail.
 */
public class AuditLogFailureException extends RuntimeException {

  public AuditLogFailureException(String message) {
    super(message);
  }

  public AuditLogFailureException(String message, Throwable cause) {
    super(message, cause);
  }
}
