package io.openaev.utils;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Utility class providing runtime predicate operations for in-memory filtering.
 *
 * <p>This class provides methods for evaluating filter conditions on objects at runtime. All string
 * comparisons are case-insensitive.
 *
 * <p>Supported operations:
 *
 * <ul>
 *   <li>Equality checks (equals, not equals)
 *   <li>Contains/substring checks
 *   <li>Prefix checks (starts with)
 *   <li>Empty/null checks
 * </ul>
 *
 * <p>This is the runtime counterpart to {@link OperationUtilsJpa}, providing the same filtering
 * logic for in-memory collections rather than database queries.
 *
 * @see FilterUtilsRuntime for building runtime predicates
 * @see OperationUtilsJpa for JPA-based filtering
 */
public class OperationUtilsRuntime {

  private OperationUtilsRuntime() {
    // Utility class - prevent instantiation
  }

  // -- NOT CONTAINS --

  /**
   * Checks if the value does not contain any of the specified texts.
   *
   * @param value the value to check
   * @param texts the texts to search for
   * @return true if the value does not contain any text
   */
  public static boolean notContainsTexts(
      @NotNull final Object value, @NotNull final List<String> texts) {
    return texts.stream().anyMatch(text -> notContainsText(value, text));
  }

  /**
   * Checks if the value does not contain the specified text (case-insensitive).
   *
   * @param value the value to check
   * @param text the text to search for
   * @return true if the value does not contain the text
   */
  public static boolean notContainsText(@NotNull final Object value, @NotBlank final String text) {
    return !containsText(value, text);
  }

  // -- CONTAINS --

  /**
   * Checks if the value contains any of the specified texts.
   *
   * @param value the value to check
   * @param texts the texts to search for
   * @return true if the value contains at least one text
   */
  public static boolean containsTexts(
      @NotNull final Object value, @NotNull final List<String> texts) {
    return texts.stream().anyMatch(text -> containsText(value, text));
  }

  /**
   * Checks if the value contains the specified text (case-insensitive).
   *
   * @param value the value to check (String or Enum)
   * @param text the text to search for
   * @return true if the value contains the text
   */
  public static boolean containsText(@NotNull final Object value, @NotBlank final String text) {
    if (value instanceof Enum<?>) {
      return ((Enum<?>) value).name().toLowerCase().contains(text.toLowerCase());
    }
    return ((String) value).toLowerCase().contains(text.toLowerCase());
  }

  // -- NOT EQUALS --

  /**
   * Checks if the value does not equal any of the specified texts.
   *
   * @param value the value to check
   * @param texts the texts to compare against
   * @return true if the value does not equal any text
   */
  public static boolean notEqualsTexts(
      @NotNull final Object value, @NotNull final List<String> texts) {
    return texts.stream().anyMatch(text -> notEqualsText(value, text));
  }

  /**
   * Checks if the value does not equal the specified text (case-insensitive).
   *
   * @param value the value to check
   * @param text the text to compare against
   * @return true if the value does not equal the text
   */
  public static boolean notEqualsText(@NotNull final Object value, @NotBlank final String text) {
    return !equalsText(value, text);
  }

  // -- EQUALS --

  /**
   * Checks if the value equals any of the specified texts.
   *
   * @param value the value to check
   * @param texts the texts to compare against
   * @return true if the value equals at least one text
   */
  public static boolean equalsTexts(
      @NotNull final Object value, @NotNull final List<String> texts) {
    return texts.stream().anyMatch(text -> equalsText(value, text));
  }

  /**
   * Checks if the value equals the specified text (case-insensitive).
   *
   * <p>Boolean values are parsed from string representations.
   *
   * @param value the value to check
   * @param text the text to compare against
   * @return true if the value equals the text
   */
  public static boolean equalsText(@NotNull final Object value, @NotBlank final String text) {
    if (value.getClass().isAssignableFrom(Boolean.class)) {
      return value.equals(Boolean.parseBoolean(text));
    } else {
      return value.toString().equalsIgnoreCase(text);
    }
  }

  // -- NOT START WITH --

  /**
   * Checks if the value does not start with any of the specified texts.
   *
   * @param value the value to check
   * @param texts the prefixes to test
   * @return true if the value does not start with any text
   */
  public static boolean notStartWithTexts(
      @NotNull final Object value, @NotNull final List<String> texts) {
    return texts.stream().anyMatch(text -> notStartWithText(value, text));
  }

  /**
   * Checks if the value does not start with the specified text (case-insensitive).
   *
   * @param value the value to check
   * @param text the prefix to test
   * @return true if the value does not start with the text
   */
  public static boolean notStartWithText(@NotNull final Object value, @NotBlank final String text) {
    return !startWithText(value, text);
  }

  // -- START WITH --

  /**
   * Checks if the value starts with any of the specified texts.
   *
   * @param value the value to check
   * @param texts the prefixes to test
   * @return true if the value starts with at least one text
   */
  public static boolean startWithTexts(
      @NotNull final Object value, @NotNull final List<String> texts) {
    return texts.stream().anyMatch(text -> startWithText(value, text));
  }

  /**
   * Checks if the value starts with the specified text (case-insensitive).
   *
   * @param value the value to check
   * @param text the prefix to test
   * @return true if the value starts with the text
   */
  public static boolean startWithText(@NotNull final Object value, @NotBlank final String text) {
    return value.toString().toLowerCase().startsWith(text.toLowerCase());
  }

  // -- NOT EMPTY --

  /**
   * Checks if the value is not empty or null.
   *
   * @param value the value to check
   * @return true if the value is not empty
   */
  public static boolean notEmpty(final Object value) {
    return !empty(value);
  }

  // -- EMPTY --

  /**
   * Checks if the value is empty or null.
   *
   * <p>A value is considered empty if it is null, or if it is a String that is blank.
   *
   * @param value the value to check
   * @return true if the value is null or empty
   */
  public static boolean empty(final Object value) {
    if (value == null) {
      return true;
    }
    if (value instanceof String stringValue) {
      return stringValue.isBlank();
    }
    return false;
  }
}
