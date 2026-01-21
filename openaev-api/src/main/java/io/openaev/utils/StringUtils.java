package io.openaev.utils;

import io.openaev.rest.exception.BadRequestException;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class StringUtils {

  public static final int MAX_SIZE_OF_STRING = 255;

  private StringUtils() {
    // Utility class - prevent instantiation
  }

  public static String duplicateString(@NotBlank final String originName) {
    String newName = originName + " (duplicate)";
    if (newName.length() > MAX_SIZE_OF_STRING) {
      newName = newName.substring(0, (MAX_SIZE_OF_STRING - 1) - " (duplicate)".length());
    }
    return newName;
  }

  public static boolean isValidRegex(String regex) {
    try {
      Pattern.compile(regex);
      return true;
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  /** Generate a random hex color in the format #RRGGBB. */
  public static String generateRandomColor() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int r = random.nextInt(256);
    int g = random.nextInt(256);
    int b = random.nextInt(256);
    return String.format("#%02X%02X%02X", r, g, b);
  }

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
