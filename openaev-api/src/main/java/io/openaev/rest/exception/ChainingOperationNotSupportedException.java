package io.openaev.rest.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A lifecycle operation the chaining engine does not support (its queue-based execution model has
 * no pause semantics). Unchecked and mapped to 400 so a direct API call gets a business message
 * instead of the 500 the checked {@link ChainingException} produced - that one stays reserved for
 * internal chaining engine failures, which must keep surfacing as 500.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ChainingOperationNotSupportedException extends RuntimeException {

  public ChainingOperationNotSupportedException(String message) {
    super(message);
  }
}
