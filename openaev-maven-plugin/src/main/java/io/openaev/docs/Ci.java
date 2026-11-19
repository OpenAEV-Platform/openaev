package io.openaev.docs;

import java.util.Locale;
import java.util.Map;

/** Detects a CI run from the {@code CI} environment variable, which CI services set. */
final class Ci {
  static final String VARIABLE = "CI";

  private Ci() {}

  static boolean isCi(Map<String, String> env) {
    String value = env.get(VARIABLE);
    if (value == null || value.isBlank()) {
      return false;
    }
    String normalized = value.strip().toLowerCase(Locale.ROOT);
    return !normalized.equals("false") && !normalized.equals("0");
  }
}
