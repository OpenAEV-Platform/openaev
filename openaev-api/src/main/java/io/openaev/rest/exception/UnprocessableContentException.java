package io.openaev.rest.exception;

public class UnprocessableContentException extends Exception {
  public UnprocessableContentException() {
    super();
  }

  public UnprocessableContentException(String errorMessage) {
    super(errorMessage);
  }
}
