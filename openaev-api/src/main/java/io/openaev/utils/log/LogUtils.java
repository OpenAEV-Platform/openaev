package io.openaev.utils.log;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.Action;
import io.openaev.database.model.AdministrationResourceType;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.log.form.LogDetailsInput;
import java.util.logging.Level;
import org.slf4j.Logger;

/**
 * Utility class for log message formatting and construction.
 *
 * <p>Provides helper methods for building standardized log messages from various input sources,
 * ensuring consistent log formatting across the application.
 *
 * <p>This is a utility class and cannot be instantiated.
 */
public class LogUtils {

  private static final String EVENT_ACCESS_ADMINISTRATION = "administration";
  private static final String EVENT_ACCESS_EXTENDED = "extended";

  private LogUtils() {}

  public static void log(Logger logger, String message, Object level) {
    Level resolvedLevel = getLogLevel(level);

    if (resolvedLevel == null) {
      logger.info(message);
    } else if (resolvedLevel == Level.SEVERE) {
      logger.error(message);
    } else if (resolvedLevel == Level.WARNING) {
      logger.warn(message);
    } else if (resolvedLevel == Level.INFO) {
      logger.info(message);
    } else if (resolvedLevel == Level.FINE) {
      logger.debug(message);
    } else {
      logger.info(message);
    }
  }

  public static Level getLogLevel(Object level) {
    Level resolvedLevel = null;

    if (level instanceof String strLevel) {
      if (Level.WARNING.getName().equalsIgnoreCase(strLevel)) {
        resolvedLevel = Level.WARNING;
      } else if (Level.INFO.getName().equalsIgnoreCase(strLevel)) {
        resolvedLevel = Level.INFO;
      } else if (Level.SEVERE.getName().equalsIgnoreCase(strLevel)) {
        resolvedLevel = Level.SEVERE;
      } else if (Level.FINE.getName().equalsIgnoreCase(strLevel)) {
        resolvedLevel = Level.FINE;
      }
    } else if (level instanceof Level logLevel) {
      resolvedLevel = logLevel;
    }

    return resolvedLevel;
  }

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

  /**
   * Builds a human-readable message for status_change events. Handles three cases:
   *
   * <ul>
   *   <li>Exercise status change: input has {@code exercise_status}
   *   <li>Scenario instant launch: input has {@code action=launch}
   *   <li>Scenario recurrence update: input has {@code scenario_recurrence} fields
   * </ul>
   */
  public static String buildStatusChangeMessage(
      JsonNode input, String entityTypeName, String displayName) {
    if (input == null) {
      return "changes status of " + entityTypeName + " `" + displayName + "`";
    }
    if (input.has("exercise_status")) {
      String newStatus = input.get("exercise_status").asText().toLowerCase();
      return "changes status of "
          + entityTypeName
          + " `"
          + displayName
          + "` to `"
          + newStatus
          + "`";
    }
    if (input.has("action") && "launch".equals(input.get("action").asText())) {
      return "launches " + entityTypeName + " `" + displayName + "`";
    }
    if (input.has("scenario_recurrence")) {
      return "updates recurrence of " + entityTypeName + " `" + displayName + "`";
    }
    return "changes status of " + entityTypeName + " `" + displayName + "`";
  }

  public static String buildAuthLogMessage(String eventScope, String eventStatus, String provider) {
    // Build human-readable message
    String message;

    if ("error".equals(eventStatus)) {
      message = "failed " + eventScope + " from provider `" + provider + "`";
    } else if ("logout".equals(eventScope)) {
      message = "logout";
    } else {
      message = eventScope + " from provider `" + provider + "`";
    }

    return message;
  }

  public static String getEventScope(Action action) {
    return switch (action) {
      case CREATE -> "create";
      case WRITE -> "update";
      case DELETE -> "delete";
      case LAUNCH -> "status_change";
      case DUPLICATE -> "duplicate";
      case READ, SEARCH -> "read";
      default -> "unknown";
    };
  }

  public static String getEventAccess(ResourceType resourceType) {
    try {
      AdministrationResourceType.valueOf(resourceType.name());
      return EVENT_ACCESS_ADMINISTRATION;
    } catch (IllegalArgumentException e) {
      return EVENT_ACCESS_EXTENDED;
    }
  }

  public static String getAuthEventAccess() {
    return EVENT_ACCESS_ADMINISTRATION;
  }

  /** Extracts a name from a snapshotted JSON node. */
  public static String extractNameFromSnapshot(JsonNode snapshot) {
    if (snapshot == null) {
      return null;
    }
    // Try common name fields in order of precedence
    for (String field :
        new String[] {
          "scenario_name",
          "exercise_name",
          "inject_title",
          "user_firstname",
          "name",
          "role_name",
          "group_name"
        }) {
      JsonNode node = snapshot.get(field);
      if (node != null && node.isTextual()) {
        return node.asText();
      }
    }
    return null;
  }
}
