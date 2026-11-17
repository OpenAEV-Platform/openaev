package io.openaev.exception;

/** An action the model refuses, with a reason shown to the user as a bad request. */
public abstract class ActionRefusedException extends RuntimeException {

  protected ActionRefusedException(String message) {
    super(message);
  }
}
