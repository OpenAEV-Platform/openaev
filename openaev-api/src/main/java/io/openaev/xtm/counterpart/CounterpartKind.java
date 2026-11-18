package io.openaev.xtm.counterpart;

import java.util.Locale;

/**
 * The kinds of objects the XTM platforms share, each matched on a real identifier.
 *
 * <p>The kind decides how a key is normalised before it is looked up and cached, so the same CVE
 * typed "cve-2024-1234" and "CVE-2024-1234" resolves once.
 */
public enum CounterpartKind {
  /** An endpoint or asset, keyed by hostname, IP address or MAC address. */
  ASSET,
  /** An attack pattern, keyed by its MITRE ATT&CK external id (T1059, T1059.001). */
  ATTACK_PATTERN,
  /** A vulnerability, keyed by its CVE id. */
  VULNERABILITY,
  /** The threat knowledge a scenario was generated from, keyed by its STIX id. */
  THREAT,
  /**
   * An alert a collector brought back from a security platform, keyed by its link in that platform:
   * the other product holds it when one of its objects carries that link as an external reference.
   */
  ALERT;

  /** Returns the key as it is matched and cached, or null when it is blank. */
  public String normalise(String key) {
    if (key == null) {
      return null;
    }
    String trimmed = key.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return switch (this) {
      case ATTACK_PATTERN, VULNERABILITY -> trimmed.toUpperCase(Locale.ROOT);
      case ASSET -> trimmed.toLowerCase(Locale.ROOT);
      case THREAT, ALERT -> trimmed;
    };
  }
}
