package io.openaev.utils;

import io.openaev.rest.exception.BadRequestException;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Utility class providing common string manipulation operations for the OpenAEV platform.
 *
 * <p>This class contains static helper methods for string operations such as duplication with
 * suffix handling, regex validation, random color generation, and blank string checks. All methods
 * are thread-safe and stateless.
 *
 * <p>This is a utility class and cannot be instantiated.
 */
public class StringUtils {
  /** Maximum allowed length for string values in the database. */
  public static final int MAX_SIZE_OF_STRING = 255;

  private StringUtils() {
    // Utility class - prevent instantiation
  }

  /** Suffix appended to duplicated entity names. */
  private static final String DUPLICATE_SUFFIX = " (duplicate)";

  /**
   * Creates a duplicate name by appending a suffix to the original name.
   *
   * <p>If the resulting name exceeds {@link #MAX_SIZE_OF_STRING}, the original name is truncated to
   * ensure the final string fits within the maximum length while preserving the duplicate suffix.
   *
   * @param originName the original name to duplicate (must not be blank)
   * @return the duplicated name with " (duplicate)" suffix
   * @throws IllegalArgumentException if originName is blank
   */
  public static String duplicateString(@NotBlank final String originName) {
    String newName = originName + DUPLICATE_SUFFIX;
    if (newName.length() > MAX_SIZE_OF_STRING) {
      // Truncate the original name to fit within MAX_SIZE_OF_STRING including the suffix
      int maxOriginalLength = MAX_SIZE_OF_STRING - DUPLICATE_SUFFIX.length();
      newName = originName.substring(0, maxOriginalLength) + DUPLICATE_SUFFIX;
    }
    return newName;
  }

  /**
   * Validates whether a string is a syntactically correct regular expression.
   *
   * @param regex the regular expression pattern to validate
   * @return {@code true} if the regex compiles successfully, {@code false} otherwise
   */
  public static boolean isValidRegex(String regex) {
    try {
      Pattern.compile(regex);
      return true;
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  /**
   * Generates a random hexadecimal color code.
   *
   * <p>The generated color is in the format {@code #RRGGBB} where each component (red, green, blue)
   * is a random value between 0 and 255.
   *
   * @return a random hex color string (e.g., "#A3F4B2")
   */
  public static String generateRandomColor() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int r = random.nextInt(256);
    int g = random.nextInt(256);
    int b = random.nextInt(256);
    return String.format("#%02X%02X%02X", r, g, b);
  }

  /**
   * Checks if a string is blank (null, empty, or contains only whitespace).
   *
   * <p>This method delegates to Apache Commons Lang's {@code StringUtils.isBlank()}.
   *
   * @param str the string to check
   * @return {@code true} if the string is null, empty, or whitespace only
   */
  public static boolean isBlank(String str) {
    return org.apache.commons.lang3.StringUtils.isBlank(str);
  }

  /**
   * Validates that the given string is a valid UUID format.
   *
   * @param uuid the string to validate as a UUID
   * @throws BadRequestException if the string is not a valid UUID format
   */
  public static void isValidUUID(String uuid) {
    try {
      if (uuid == null) {
        throw new IllegalArgumentException("Uuid value is null");
      }
      UUID.fromString(uuid);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid import ID format, It couldn't be parsed as UUID.");
    }
  }
}
