package io.openaev.security.error;

public class AuthenticationError extends Exception {
  public AuthenticationError(String message) {
    super(message);
  }

  public AuthenticationError(String message, Throwable cause) {
    super(message, cause);
  }
}
