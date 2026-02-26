package io.openaev.rest.exception;

public class ChainingException extends Exception {

  public ChainingException(String errorMessage) {
    super(errorMessage);
  }

  public ChainingException(String errorMessage, Exception cause) {
    super(errorMessage, cause);
  }
}
