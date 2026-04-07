package io.openaev.api.exception;

public class AlreadyExistingException extends RuntimeException {

  public AlreadyExistingException() {
    super();
  }

  public AlreadyExistingException(String errorMessage) {
    super(errorMessage);
  }
}
