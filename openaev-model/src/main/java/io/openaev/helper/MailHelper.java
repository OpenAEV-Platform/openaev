package io.openaev.helper;

/**
 * Utility class for email-related operations.
 *
 * <p>Provides helper methods for deriving and sanitizing sender display names from email addresses.
 */
public class MailHelper {

  public static final int FROM_NAME_MAX_LENGTH = 100;
  public static final String FROM_NAME_PATTERN = "^[^\\r\\n\\x00]*$";

  public static final String FROM_NAME_PATTERN_MESSAGE =
      "Display name must not contain control characters";

  public static final String FROM_NAME_SIZE_MESSAGE =
      "Display name must be " + FROM_NAME_MAX_LENGTH + " characters or less";

  private MailHelper() {}

  public static String resolveFromName(String fromName, String from) {
    String resolved = null;
    if (fromName != null && !fromName.isBlank()) {
      resolved = fromName;
    } else if (from != null && from.contains("@")) {
      resolved = from.substring(0, from.indexOf('@'));
    }
    return sanitize(resolved);
  }

  private static String sanitize(String name) {
    if (name == null) {
      return null;
    }
    String clean = name.replaceAll("[\\r\\n\\x00]", "").trim();
    if (clean.isEmpty()) {
      return null;
    }
    if (clean.length() > FROM_NAME_MAX_LENGTH) {
      clean = clean.substring(0, FROM_NAME_MAX_LENGTH);
    }
    return clean;
  }
}
