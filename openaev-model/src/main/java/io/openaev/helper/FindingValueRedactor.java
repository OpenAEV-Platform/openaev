package io.openaev.helper;

/**
 * Redacts the value of a sensitive finding before it leaves the platform through the API. The
 * database row keeps the full cleartext value (it is needed for deduplication, correlation and
 * attack path computation): only the serialized representation is masked.
 *
 * <p>The redaction is partial on purpose, so an operator can still tell WHICH secret was discovered
 * when the value is already known to them, without the API ever disclosing the secret itself:
 *
 * <ul>
 *   <li>{@code jdoe:Sup3rS3cret} becomes {@code jdoe:******} - the identity part (before the first
 *       {@code :}) is kept, the secret part is fully masked
 *   <li>{@code Sup3rS3cret} becomes {@code Su******} - a two character fragment is kept
 *   <li>a short value is masked entirely, since a fragment would disclose most of it
 * </ul>
 */
public final class FindingValueRedactor {

  public static final String MASK = "******";

  private static final char IDENTITY_SEPARATOR = ':';
  private static final int VISIBLE_FRAGMENT_LENGTH = 2;
  private static final int MIN_LENGTH_FOR_FRAGMENT = 5;

  private FindingValueRedactor() {}

  /**
   * @param value the cleartext finding value
   * @param sensitive whether the finding holds sensitive material
   * @return the value as-is when the finding is not sensitive, its redacted form otherwise
   */
  public static String redact(final String value, final boolean sensitive) {
    if (!sensitive || value == null || value.isBlank()) {
      return value;
    }

    int separatorIndex = value.indexOf(IDENTITY_SEPARATOR);
    if (separatorIndex > 0 && separatorIndex < value.length() - 1) {
      return value.substring(0, separatorIndex + 1) + MASK;
    }
    return maskWithFragment(value);
  }

  private static String maskWithFragment(final String value) {
    if (value.length() < MIN_LENGTH_FOR_FRAGMENT) {
      return MASK;
    }
    return value.substring(0, VISIBLE_FRAGMENT_LENGTH) + MASK;
  }
}
