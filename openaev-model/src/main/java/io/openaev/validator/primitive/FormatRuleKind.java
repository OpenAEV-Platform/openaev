package io.openaev.validator.primitive;

import io.openaev.validator.IpAddressUtils;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.apache.commons.validator.routines.EmailValidator;

/**
 * Catalogue of the atomic format rules a {@link io.openaev.database.model.PrimitiveType} can be
 * validated against.
 *
 * <p>A kind is either:
 *
 * <ul>
 *   <li><b>serializable</b> - backed by a regex whose syntax lives in the Java ∩ ECMAScript
 *       intersection, so the exact same pattern can be shipped to the frontend and executed with
 *       {@code new RegExp()}. {@link #pattern()} is then non-null.
 *   <li><b>named</b> - backed by a parser that cannot be faithfully expressed as a regex ({@code
 *       InetAddress}, Apache Commons {@code EmailValidator}). {@link #pattern()} is null and the
 *       frontend must provide its own implementation for the same identifier, pinned by the shared
 *       test vectors in {@code primitive-format-vectors.json}.
 * </ul>
 *
 * <p>Serializable kinds are authoritative through their regex: the backend validates acceptance
 * with the pattern itself, never with a looser parser. {@code PORT} and {@code NUMBER} are the
 * notable case - {@code Integer.parseInt} / {@code Double.parseDouble} accept forms the pattern
 * rejects ({@code +80}, {@code 1e5}, {@code NaN}), and those parsers stay reserved for evaluation
 * time comparison, not for input acceptance.
 */
public enum FormatRuleKind {

  // -- Named kinds: delegated parsers, not expressible as a portable regex --

  IPV4(null, IpAddressUtils::isIpv4Address, "Expected a valid IPv4 address"),
  IPV6(null, IpAddressUtils::isIpv6Address, "Expected a valid IPv6 address"),
  IPV4_CIDR(null, IpAddressUtils::isIpv4Subnet, "Expected a valid IPv4 subnet"),
  IPV6_CIDR(null, IpAddressUtils::isIpv6Subnet, "Expected a valid IPv6 subnet"),
  EMAIL(
      null, value -> EmailValidator.getInstance().isValid(value), "Expected a valid email address"),

  // -- Serializable kinds: the regex is the single source of truth --

  /**
   * Domain or host name, including single-label names ({@code localhost}, {@code dc01}) and
   * internationalized labels.
   *
   * <p>Deliberately does <b>not</b> validate the TLD against the IANA list, unlike Apache Commons
   * {@code DomainValidator}: private suffixes such as {@code .corp}, {@code .lan} or {@code
   * .internal} are first-class citizens in an adversary-simulation scope, and the IANA list shipped
   * inside the commons-validator jar would silently change acceptance on a dependency bump.
   *
   * <p>The trailing {@code (?![0-9]+$)} guard keeps the rule from accepting an IPv4 literal as a
   * domain once the TLD constraint is dropped, while still allowing names like {@code srv01}.
   */
  DOMAIN(DomainPatterns.DOMAIN, null, "Expected a valid domain name"),

  PORT(
      "^(?:6553[0-5]|655[0-2][0-9]|65[0-4][0-9]{2}|6[0-4][0-9]{3}|[1-5][0-9]{4}|[0-9]{1,4})$",
      null, "Expected a valid port (0-65535)"),
  NUMBER("^-?[0-9]+(?:\\.[0-9]+)?$", null, "The value should be a number"),
  CVE("^CVE-[0-9]{4}-[0-9]{4,}$", null, "Expected a valid CVE identifier (e.g. CVE-2024-12345)"),
  WINDOWS_SID("^S-1-[0-9]+(?:-[0-9]+)+$", null, "Expected a valid Windows SID (e.g. S-1-5-21-...)");

  private final String pattern;
  private final Pattern compiledPattern;
  private final Predicate<String> parser;
  private final String errorMessageKey;

  FormatRuleKind(String pattern, Predicate<String> parser, String errorMessageKey) {
    this.pattern = pattern;
    this.compiledPattern = pattern != null ? Pattern.compile(pattern) : null;
    this.parser = parser;
    this.errorMessageKey = errorMessageKey;
  }

  /**
   * The ECMAScript-compatible pattern backing this kind, or {@code null} when the kind is a named
   * one that the frontend must implement separately.
   */
  public String pattern() {
    return this.pattern;
  }

  /** {@code true} when the pattern can be shipped to and executed by the frontend as-is. */
  public boolean isSerializable() {
    return this.pattern != null;
  }

  /**
   * Stable i18n key, never a translated sentence: the frontend owns the translation and passes this
   * value to {@code t()}.
   */
  public String errorMessageKey() {
    return this.errorMessageKey;
  }

  /** Evaluates the rule. A null or blank value is never accepted by a format rule. */
  public boolean matches(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    String trimmed = value.trim();
    if (this.compiledPattern != null) {
      return this.compiledPattern.matcher(trimmed).matches();
    }
    return this.parser.test(trimmed);
  }

  /**
   * Pattern fragments kept in a nested holder: enum constant initializers run before the enum's own
   * static fields, so the composed domain pattern cannot live directly in {@link FormatRuleKind}.
   */
  private static final class DomainPatterns {

    /**
     * Latin-1 Supplement / Latin Extended, Cyrillic and CJK ranges, mirroring the frontend rule.
     */
    private static final String IDN = "\\u00C0-\\u024F\\u0400-\\u04FF\\u4E00-\\u9FFF";

    private static final String LABEL_EDGE = "[a-zA-Z0-9" + IDN + "]";
    private static final String LABEL_INNER = "[a-zA-Z0-9" + IDN + "-]";

    /** One DNS label: 1 to 63 characters, never starting nor ending with a hyphen. */
    private static final String LABEL =
        LABEL_EDGE + "(?:" + LABEL_INNER + "{0,61}" + LABEL_EDGE + ")?";

    private static final String DOMAIN =
        "^(?=.{1,253}$)(?:" + LABEL + "\\.)*(?![0-9]+$)" + LABEL + "$";

    private DomainPatterns() {}
  }
}
