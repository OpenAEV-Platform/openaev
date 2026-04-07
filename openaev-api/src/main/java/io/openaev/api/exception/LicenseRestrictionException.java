package io.openaev.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class LicenseRestrictionException extends RuntimeException {
  public LicenseRestrictionException(String message) {
    super(message);
  }
}
