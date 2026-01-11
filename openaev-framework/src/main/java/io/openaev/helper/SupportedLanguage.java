package io.openaev.helper;

import java.util.Arrays;

/**
 * Enumeration of supported languages in the OpenAEV platform.
 *
 * <p>This enum provides language constants and utility methods for parsing language values from
 * strings. It is used throughout the platform for internationalization (i18n) of:
 *
 * <ul>
 *   <li>Injection labels and descriptions
 *   <li>Contract field labels
 *   <li>User-facing messages
 *   <li>Report generation
 * </ul>
 *
 * <p>The default language is English ({@link #en}).
 */
public enum SupportedLanguage {
  /** French language */
  fr,
  /** English language (default) */
  en;

  /** The default language used when no specific language is requested or available. */
  public static final SupportedLanguage DEFAULT = en;

  @Override
  public String toString() {
    return name();
  }

  /**
   * Returns a SupportedLanguage enum constant representing the specified value.
   *
   * <p>This method handles the following cases:
   *
   * <ul>
   *   <li>"auto" - returns the default language (English)
   *   <li>null or empty - returns the default language (English)
   *   <li>valid language code - returns the corresponding enum constant
   *   <li>invalid value - returns the default language (English) with logging
   * </ul>
   *
   * @param value the value to search for (case-insensitive)
   * @return the SupportedLanguage enum constant representing the specified value, or {@link
   *     #DEFAULT} if the value is null, empty, "auto", or not recognized
   */
  public static SupportedLanguage of(String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT;
    }

    String normalizedValue = value.toLowerCase().trim();

    if ("auto".equals(normalizedValue)) {
      return DEFAULT;
    }

    return Arrays.stream(values())
        .filter(lang -> lang.name().equalsIgnoreCase(normalizedValue))
        .findFirst()
        .orElse(DEFAULT);
  }

  /**
   * Checks if the given value represents a valid supported language.
   *
   * @param value the value to check
   * @return true if the value is a valid language code, false otherwise
   */
  public static boolean isSupported(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    return Arrays.stream(values()).anyMatch(lang -> lang.name().equalsIgnoreCase(value.trim()));
  }
}
