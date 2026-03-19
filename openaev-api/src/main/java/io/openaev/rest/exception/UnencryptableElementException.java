package io.openaev.rest.exception;

public class UnencryptableElementException extends RuntimeException {
  public UnencryptableElementException(String errorMessage, Exception cause) {
    super(errorMessage, cause);
  }
}
