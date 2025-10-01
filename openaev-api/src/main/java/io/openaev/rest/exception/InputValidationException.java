package io.openaev.rest.exception;

public class InputValidationException extends Exception {

  private final String field;

  public InputValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
