package io.openaev.utils;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class OperationUtilsRuntime {

  private OperationUtilsRuntime() {
    // Utility class - prevent instantiation
  }

  // -- NOT CONTAINS --

  public static boolean notContainsTexts(
      @NotNull final Object value, @NotNull final List<String> texts) {
    return texts.stream().anyMatch(text -> notContainsText(value, text));
  }

  public static boolean notContainsText(@NotNull final Object value, @NotBlank final String text) {
    return !containsText(value, text);
  }

  // -- CONTAINS --

  public static boolean containsTexts(
      @NotNull final Object value, @NotNull final List<String> texts) {
    return texts.stream().anyMatch(text -> containsText(value, text));
  }

  public static boolean containsText(@NotNull final Object value, @NotBlank final String text) {
    if (value instanceof Enum<?>) {
      return ((Enum<?>) value).name().toLowerCase().contains(text.toLowerCase());
    }
    return ((String) value).toLowerCase().contains(text.toLowerCase());
  }

  // -- NOT EQUALS --

  public static boolean notEqualsTexts(
      @NotNull final Object value, @NotNull final List<String> texts) {
    return texts.stream().anyMatch(text -> notEqualsText(value, text));
  }

  public static boolean notEqualsText(@NotNull final Object value, @NotBlank final String text) {
    return !equalsText(value, text);
  }

  // -- EQUALS --

  public static boolean equalsTexts(
      @NotNull final Object value, @NotNull final List<String> texts) {
    return texts.stream().anyMatch(text -> equalsText(value, text));
  }

  public static boolean equalsText(@NotNull final Object value, @NotBlank final String text) {
    if (value.getClass().isAssignableFrom(Boolean.class)) {
      return value.equals(Boolean.parseBoolean(text));
    } else {
      return value.toString().equalsIgnoreCase(text);
    }
  }

  // -- NOT START WITH --

  public static boolean notStartWithTexts(
      @NotNull final Object value, @NotNull final List<String> texts) {
    return texts.stream().anyMatch(text -> notStartWithText(value, text));
  }

  public static boolean notStartWithText(@NotNull final Object value, @NotBlank final String text) {
    return !startWithText(value, text);
  }

  // -- START WITH --

  public static boolean startWithTexts(
      @NotNull final Object value, @NotNull final List<String> texts) {
    return texts.stream().anyMatch(text -> startWithText(value, text));
  }

  public static boolean startWithText(@NotNull final Object value, @NotBlank final String text) {
    return value.toString().toLowerCase().startsWith(text.toLowerCase());
  }

  // -- NOT EMPTY --

  public static boolean notEmpty(final Object value) {
    return !empty(value);
  }

  // -- EMPTY --

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
