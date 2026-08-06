package io.openaev.rest.exception;

/**
 * Raised when a mutation is attempted on a simulation's chaining logic map (step templates,
 * condition trees or workflow configuration) while the owning simulation is no longer editable
 * (i.e. it has been launched and is not in the {@code SCHEDULED} state).
 *
 * <p>Mapped to HTTP 403 (via {@link ForbiddenException}) as a forbidden state-transition, not an
 * RBAC denial. See ADR-005.
 */
public class WorkflowNotEditableException extends ForbiddenException {

  public WorkflowNotEditableException(String message) {
    super(message);
  }
}
