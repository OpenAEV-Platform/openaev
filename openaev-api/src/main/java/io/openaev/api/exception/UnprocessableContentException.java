package io.openaev.api.exception;

public class UnprocessableContentException extends Exception {
  public UnprocessableContentException() {
    super();
  }

  public UnprocessableContentException(String errorMessage) {
    super(errorMessage);
  }
}
