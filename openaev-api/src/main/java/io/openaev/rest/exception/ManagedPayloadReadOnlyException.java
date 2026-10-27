package io.openaev.rest.exception;

/** A managed payload version (ADR-018) is read-only: never edited nor deleted, only duplicated. */
public class ManagedPayloadReadOnlyException extends ForbiddenException {

  public ManagedPayloadReadOnlyException(String message) {
    super(message);
  }
}
