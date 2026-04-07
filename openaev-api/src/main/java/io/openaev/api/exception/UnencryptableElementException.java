package io.openaev.api.exception;

public class UnencryptableElementException extends RuntimeException {
  public UnencryptableElementException(String errorMessage, Exception cause) {
    super(errorMessage, cause);
  }
}
