package io.openaev.rest.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Raised when an inject credential cannot be resolved. Carries one of the credential error codes:
 * the resolution endpoint returns the code as is, so the injector can report it in the inject
 * execution trace.
 */
@Getter
public class CredentialResolutionException extends RuntimeException {

  @Getter
  public enum ErrorCode {
    /** The credential no longer exists, or does not belong to the inject. */
    CREDENTIAL_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** The credential exists but its last connectivity check left it unusable. */
    CREDENTIAL_INACTIVE(HttpStatus.BAD_REQUEST),
    /** The execution is not entitled to the credential, or the credential is refused access. */
    CREDENTIAL_ACCESS_DENIED(HttpStatus.FORBIDDEN);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
      this.status = status;
    }
  }

  private final ErrorCode errorCode;

  public CredentialResolutionException(ErrorCode errorCode) {
    super(errorCode.name());
    this.errorCode = errorCode;
  }
}
