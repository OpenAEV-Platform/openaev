package io.openaev.rest.exception;

/**
 * Raised when a managed payload version (one coming from a repository release) is edited. Managed
 * versions are read-only; users duplicate them into a local copy instead. See ADR-011.
 */
public class ManagedPayloadNotEditableException extends ForbiddenException {

  public ManagedPayloadNotEditableException(String message) {
    super(message);
  }
}
