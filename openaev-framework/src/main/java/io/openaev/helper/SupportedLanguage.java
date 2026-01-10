package io.openaev.helper;

public enum SupportedLanguage {
  fr,
  en;

  @Override
  public String toString() {
    return name();
  }

  /**
   * Returns a SupportedLanguage enum constant representing the specified value.
   *
   * @param value the value to search for
   * @return the SupportedLanguage enum constant representing the specified value.
   */
  public static SupportedLanguage of(String value) {
    return switch (value.toLowerCase()) {
      case "auto" -> en;
      default -> valueOf(value);
    };
  }
}
