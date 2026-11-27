package io.openaev.execution;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The inject's action version is not an approved version: like a revoked payload, it is refused at
 * run time and the inject fails.
 */
@ResponseStatus(HttpStatus.GONE)
public class PayloadNotApprovedException extends ExecutionExecutorException {
  public PayloadNotApprovedException(String message) {
    super(message);
  }
}
