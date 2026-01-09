package io.openaev.rest.exception;

public class UncypherableElementException extends RuntimeException {
  public UncypherableElementException() {
    super();
  }

  public UncypherableElementException(String errorMessage) {
    super(errorMessage);
  }

  public UncypherableElementException(String errorMessage, Exception cause) {
    super(errorMessage, cause);
  }
}
