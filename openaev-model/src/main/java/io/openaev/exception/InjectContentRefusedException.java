package io.openaev.exception;

public class InjectContentRefusedException extends ActionRefusedException {

  private InjectContentRefusedException(String message) {
    super(message);
  }

  public static InjectContentRefusedException singleValueExpected(String fieldKey) {
    return new InjectContentRefusedException(
        "The field '%s' takes a single value, not a list.".formatted(fieldKey));
  }
}
