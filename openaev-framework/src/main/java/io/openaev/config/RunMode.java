package io.openaev.config;

import java.util.Locale;

public enum RunMode {
  NORMAL("normal"),
  SAFE("safe");

  private final String value;

  RunMode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static RunMode fromValue(String value) {
    if (value == null) {
      return NORMAL;
    }
    String normalized = value.toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "safe" -> SAFE;
      case "normal" -> NORMAL;
      default -> NORMAL;
    };
  }
}
