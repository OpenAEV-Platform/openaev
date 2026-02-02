package io.openaev.service.exception;

public class InjectorRegistrationException extends Exception {
  public InjectorRegistrationException(String message) {
    super(message);
  }

  public InjectorRegistrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
