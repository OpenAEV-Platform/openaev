package io.openaev.debug;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Masks secrets and personal data before they reach the logs, by sensitive key name and by value
 * pattern (JWT, auth headers, PEM keys, emails). Immutable; built only when debug mode is active.
 */
public class SensitiveDataMasker {

  /** Cap on the characters value patterns scan, so a huge value can't burn the request thread. */
  static final int MAX_SCAN_LENGTH = 8192;

  // Single-quoted SQL literal; linear form (no nested-quantifier backtracking).
  private static final Pattern SQL_STRING_LITERAL = Pattern.compile("'[^']*(?:''[^']*)*'");

  private final boolean enabled;
  private final boolean maskAllParameters;
  private final String mask;
  private final List<String> sensitiveKeys;
  private final List<Pattern> valuePatterns;

  public SensitiveDataMasker(DebugProperties.Masking config) {
    this.enabled = config.isEnabled();
    this.maskAllParameters = config.isMaskAllParameters();
    this.mask = config.getMask();
    this.sensitiveKeys =
        config.getSensitiveKeys().stream().map(k -> k.toLowerCase(Locale.ROOT)).toList();
    this.valuePatterns = config.getValuePatterns().stream().map(Pattern::compile).toList();
  }

  /** Deny-by-default mode: every value masked, only key and type kept. */
  public boolean isMaskAllParameters() {
    return enabled && maskAllParameters;
  }

  public boolean isSensitiveKey(String key) {
    if (key == null) {
      return false;
    }
    String lower = key.toLowerCase(Locale.ROOT);
    return sensitiveKeys.stream().anyMatch(lower::contains);
  }

  /** Masks a value: fully if its key is sensitive (or mask-all), else by value pattern. */
  public String maskValue(String key, Object value) {
    if (!enabled) {
      return String.valueOf(value);
    }
    if (maskAllParameters || isSensitiveKey(key)) {
      return mask;
    }
    // Bound the scan (the value is truncated far shorter for display, so nothing past it is shown).
    String text = String.valueOf(value);
    if (text.length() > MAX_SCAN_LENGTH) {
      text = text.substring(0, MAX_SCAN_LENGTH);
    }
    return maskText(text);
  }

  /** Masks value patterns in the text it is given. Scans the whole argument. */
  public String maskText(String text) {
    if (!enabled || text == null || text.isEmpty()) {
      return text;
    }
    String result = text;
    for (Pattern pattern : valuePatterns) {
      result = pattern.matcher(result).replaceAll(mask);
    }
    return result;
  }

  /**
   * Statement text for logging, bounded to {@link #MAX_SCAN_LENGTH} so a huge statement (e.g. a
   * long {@code IN (...)} list) cannot burn the request thread or leak past the cap. Mask-all also
   * blanks string literals.
   */
  public String maskStatementText(String sql) {
    if (!enabled || sql == null || sql.isEmpty()) {
      return sql;
    }
    String bounded =
        sql.length() > MAX_SCAN_LENGTH
            ? sql.substring(0, MAX_SCAN_LENGTH) + " ...(truncated)"
            : sql;
    String masked = maskText(bounded);
    if (!maskAllParameters) {
      return masked;
    }
    return SQL_STRING_LITERAL.matcher(masked).replaceAll(mask);
  }
}
