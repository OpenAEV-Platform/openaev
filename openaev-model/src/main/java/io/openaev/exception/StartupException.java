package io.openaev.exception;

import lombok.Getter;

/**
 * Exception thrown when a critical error occurs during application startup.
 *
 * <p>This exception indicates a fatal condition that prevents the application from starting
 * correctly. When thrown during initialization, it typically causes the application to terminate.
 *
 * <p>Common scenarios:
 *
 * <ul>
 *   <li>Required external services are unavailable (database, message queue, search engine)
 *   <li>Configuration validation failures
 *   <li>Missing required environment variables
 *   <li>Incompatible database schema versions
 * </ul>
 */
@Getter
public class StartupException extends RuntimeException {

  /**
   * Constructs a new startup exception with the specified message.
   *
   * @param message the detail message describing the startup failure
   */
  public StartupException(String message) {
    super(message);
  }

  /**
   * Constructs a new startup exception with the specified message and cause.
   *
   * @param message the detail message describing the startup failure
   * @param e the underlying exception that caused this error
   */
  public StartupException(String message, Exception e) {
    super(message, e);
  }
}
