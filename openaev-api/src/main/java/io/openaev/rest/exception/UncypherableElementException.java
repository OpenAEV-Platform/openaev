package io.openaev.rest.exception;

public class UncypherableElementException extends RuntimeException {
  public UncypherableElementException(String errorMessage, Exception cause) {
    super(errorMessage, cause);
  }
}
