package io.openaev.execution;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The inject's payload is revoked: it is refused before execution (F9, F18). */
@ResponseStatus(HttpStatus.GONE)
public class PayloadRevokedException extends ExecutionExecutorException {
  public PayloadRevokedException(String message) {
    super(message);
  }
}
