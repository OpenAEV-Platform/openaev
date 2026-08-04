package io.openaev.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a date range is invalid (e.g. start date is after end date).
 *
 * <p>Maps to HTTP 400 Bad Request so that callers receive a clear validation error instead of a 500
 * from the search engine.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidDateRangeException extends RuntimeException {

  public InvalidDateRangeException(String message) {
    super(message);
  }
}
