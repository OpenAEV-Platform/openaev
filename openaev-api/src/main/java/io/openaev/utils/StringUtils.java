package io.openaev.utils;

import jakarta.validation.constraints.NotBlank;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class StringUtils {

  private StringUtils() {}

  public static final int MAX_SIZE_OF_STRING = 255;

  private static final String DUPLICATE_SUFFIX = " (duplicate)";

  public static String duplicateString(@NotBlank final String originName) {
    String newName = originName + DUPLICATE_SUFFIX;
    if (newName.length() > MAX_SIZE_OF_STRING) {
      // Truncate the original name to fit within MAX_SIZE_OF_STRING including the suffix
      int maxOriginalLength = MAX_SIZE_OF_STRING - DUPLICATE_SUFFIX.length();
      newName = originName.substring(0, maxOriginalLength) + DUPLICATE_SUFFIX;
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
}
