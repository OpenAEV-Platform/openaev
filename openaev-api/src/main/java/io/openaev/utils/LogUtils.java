package io.openaev.utils;

import io.openaev.rest.log.form.LogDetailsInput;

/**
 * Utility class for log message formatting and construction.
 *
 * <p>Provides helper methods for building standardized log messages from various input sources,
 * ensuring consistent log formatting across the application.
 *
 * <p>This is a utility class and cannot be instantiated.
 */
public class LogUtils {

  private LogUtils() {}

  /**
   * Builds a formatted log message from log details input.
   *
   * <p>Constructs a log message string that includes the log level, the original message, and any
   * associated stack trace information.
   *
   * @param logDetailsInput the log details containing the message and stack trace
   * @param level the log level (e.g., "INFO", "ERROR", "WARN")
   * @return a formatted log message string combining all components
   */
  public static String buildLogMessage(LogDetailsInput logDetailsInput, String level) {
    return "Message "
        + level
        + " received: "
        + logDetailsInput.getMessage()
        + " stacktrace: "
        + logDetailsInput.getStack();
  }
}
