package io.openaev.service.exception;

public class HealthCheckFailureException extends Exception {
  public HealthCheckFailureException(String message) {
    super(message);
  }

  public HealthCheckFailureException(String message, Throwable cause) {
    super(message, cause);
  }
}
