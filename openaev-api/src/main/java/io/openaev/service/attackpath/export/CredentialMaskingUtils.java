package io.openaev.service.attackpath.export;

import java.util.Comparator;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared credential/secret masking for the Attack Chaining CSV export, applied to every
 * command/payload and terminal output column regardless of row type (chokepoint or trace).
 *
 * <p>Two complementary strategies are combined:
 *
 * <ol>
 *   <li>{@link #maskKnownSecrets(String, Set)} — secrets known from {@code credentials}-type
 *       findings recorded elsewhere in the simulation, replaced wherever they recur (mirrors the
 *       execution-detail drawer, {@code AttackPathGraphService}).
 *   <li>{@link #maskGenericSecrets(String)} — a pattern-based fallback for two shapes that are
 *       unambiguous regardless of context: a {@code user:password@host} credential, and a bare
 *       NTLM/SHA hex hash. (A CLI {@code -p <value>} flag was deliberately left out: it collides
 *       with legitimate non-secret usages such as {@code nmap -p 445,3389}, so masking it would
 *       corrupt port lists and other non-credential data.)
 * </ol>
 */
final class CredentialMaskingUtils {

  static final String MASK = "••••";

  // user:password@host (e.g. "svc_backup:Summer2026!@10.10.20.15"). "CORP/administrator@10.10.10.5"
  // is left alone since it has no ':' password segment before the '@'.
  private static final Pattern USER_PASSWORD_AT_HOST =
      Pattern.compile("([\\w.\\\\/-]+):([^\\s@:'\"]+)@");

  // Bare hex hash (NTLM/SHA/etc): 32, 40 or 64 contiguous hex characters, not part of a longer
  // hex/alphanumeric token (word boundary on both sides).
  private static final Pattern HEX_HASH =
      Pattern.compile("(?<![0-9a-fA-F])[0-9a-fA-F]{32,64}(?![0-9a-fA-F])");

  private CredentialMaskingUtils() {}

  /** Masks known finding-derived secrets first, then any remaining generic secret pattern. */
  static String maskAll(String text, Set<String> knownSecrets) {
    return maskGenericSecrets(maskKnownSecrets(text, knownSecrets));
  }

  /** Replaces each known credential secret with the fixed mask wherever it appears in free text. */
  static String maskKnownSecrets(String text, Set<String> knownSecrets) {
    if (text == null || knownSecrets == null || knownSecrets.isEmpty()) {
      return text;
    }
    String masked = text;
    // Longest secret first, so a secret that is a substring of another does not corrupt the longer
    // one before it is masked (e.g. "pass" must not break "password").
    for (String secret :
        knownSecrets.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
      masked = masked.replace(secret, MASK);
    }
    return masked;
  }

  /**
   * Masks secrets recognizable purely from their shape in the text itself (no finding needed):
   * {@code user:password@host} credentials and bare NTLM/SHA hashes.
   */
  static String maskGenericSecrets(String text) {
    if (text == null || text.isBlank()) {
      return text;
    }
    String masked = USER_PASSWORD_AT_HOST.matcher(text).replaceAll("$1:" + MASK + "@");
    masked = HEX_HASH.matcher(masked).replaceAll(MASK);
    return masked;
  }
}
