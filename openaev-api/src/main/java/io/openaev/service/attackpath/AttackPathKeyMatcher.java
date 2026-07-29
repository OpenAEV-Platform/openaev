package io.openaev.service.attackpath;

import io.openaev.database.model.attackpath.projection.AttackPathFindingRow;
import io.openaev.service.attackpath.dto.ConsumedFindingKeyDTO;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Matches a produced finding against a consumed finding key, mirroring the front's finding matcher
 * so the producer the backend resolves is the finding the front would match. The key type is
 * reconciled to the finding-type vocabulary, then the operator is applied: {@code EQ} (value
 * equals), {@code IN} (comma-separated membership, single token falls back to substring), and
 * {@code IS_NOT_NULL} (presence). Every other operator matches nothing. A key whose type reconciles
 * to the finding type tests the whole value; a key targeting a SUB-FIELD of a multi-field complex
 * finding (e.g. {@code port} of a {@code portscan} value {@code host:port (service)}) is resolved
 * through a per-type sub-field extractor.
 */
public final class AttackPathKeyMatcher {

  // The complex sub-field keys whose vocabulary differs from the finding type; every other key type
  // maps to a finding type 1:1 (identity).
  private static final Map<String, String> KEYTYPE_TO_FINDING_TYPE =
      Map.of("share_name", "file", "password", "credentials");

  private AttackPathKeyMatcher() {}

  /** The finding type a consumed key type reconciles to (identity when there is no mapping). */
  public static String reconciledType(String keyType) {
    // Map.of() rejects a null lookup (NPE), and a null key type never matches a finding anyway.
    if (keyType == null) {
      return null;
    }
    return KEYTYPE_TO_FINDING_TYPE.getOrDefault(keyType, keyType);
  }

  /**
   * The finding types a key of this type can match: its reconciled/identity type plus every complex
   * type that carries it as a sub-field (e.g. {@code port} → {@code {port, portscan}}). Empty for a
   * null key type. The caller uses this to pick which finding-type buckets to scan before matching,
   * since a primitive key can reach both a primitive finding and a complex finding's sub-field.
   */
  public static Set<String> candidateFindingTypes(String keyType) {
    if (keyType == null) {
      return Set.of();
    }
    Set<String> types = new LinkedHashSet<>();
    types.add(reconciledType(keyType));
    SUBFIELDS.forEach(
        (findingType, subfields) -> {
          if (subfields.containsKey(keyType)) {
            types.add(findingType);
          }
        });
    return types;
  }

  // Per-type sub-field extractors, keyed by (finding type, key type): the inverse of each
  // *OutputProcessor.toFindingValue. Only multi-field complex types whose sub-fields events consume
  // are registered; a finding type absent here is matched on its whole value. Keyed on the
  // PRESENTED
  // type (a share is a file here). portscan first (T1); the other types follow.
  private static final Map<String, Map<String, Function<String, String>>> SUBFIELDS =
      Map.of(
          "portscan", Map.of("port", AttackPathKeyMatcher::extractPortscanPort),
          "credentials",
              Map.of(
                  "username", AttackPathKeyMatcher::extractCredentialsUsername,
                  "password", AttackPathKeyMatcher::extractCredentialsPassword),
          "file", Map.of("share_name", AttackPathKeyMatcher::extractShareName),
          "username",
              Map.of(
                  "username", AttackPathKeyMatcher::extractUsernameFindingUser,
                  "domain", AttackPathKeyMatcher::extractUsernameFindingDomain));

  public static boolean matches(AttackPathFindingRow finding, ConsumedFindingKeyDTO key) {
    if (finding == null || key == null) {
      return false;
    }
    String candidate = resolveCandidate(finding, key.keyType());
    if (candidate == null) {
      return false;
    }
    return switch (key.operator() == null ? "" : key.operator()) {
      case "IS_NOT_NULL" -> !candidate.isBlank();
      case "EQ" -> key.value() != null && key.value().equals(candidate);
      case "IN" -> matchesIn(candidate, key.value());
      default -> false;
    };
  }

  /**
   * The value tested against the key: the extracted sub-field when the finding is a multi-field
   * complex type and the key targets one of its sub-fields, else the whole finding value when the
   * key type reconciles to the finding type. Null when the finding cannot satisfy this key type, or
   * the sub-field does not parse — both a non-match.
   */
  private static String resolveCandidate(AttackPathFindingRow finding, String keyType) {
    String findingType = finding.type();
    // Immutable Map.of() throws on a null lookup, and a null key/finding type can never be a
    // registered sub-field, so guard before the sub-field lookup and fall to the whole-value path.
    if (keyType != null && findingType != null) {
      Function<String, String> extractor =
          SUBFIELDS.getOrDefault(findingType, Map.of()).get(keyType);
      if (extractor != null) {
        return extractor.apply(finding.value());
      }
    }
    String reconciledType = reconciledType(keyType);
    return reconciledType != null && reconciledType.equals(findingType) ? finding.value() : null;
  }

  // portscan value is "host:port (service)"; the port is the token after the LAST ':' once the
  // optional " (service)" suffix is dropped, so an IPv6 host's own ':' is never mistaken for it.
  private static String extractPortscanPort(String value) {
    if (value == null) {
      return null;
    }
    String v = value;
    int paren = v.indexOf(" (");
    if (paren >= 0) {
      v = v.substring(0, paren);
    }
    int lastColon = v.lastIndexOf(':');
    if (lastColon < 0 || lastColon == v.length() - 1) {
      return null;
    }
    return v.substring(lastColon + 1).trim();
  }

  // credentials value is "username:password" (CredentialsOutputProcessor); split on the FIRST ':',
  // since a username has none but a password may.
  private static String extractCredentialsUsername(String value) {
    if (value == null) {
      return null;
    }
    int i = value.indexOf(':');
    return i < 0 ? null : value.substring(0, i);
  }

  private static String extractCredentialsPassword(String value) {
    if (value == null) {
      return null;
    }
    int i = value.indexOf(':');
    return i < 0 || i == value.length() - 1 ? null : value.substring(i + 1);
  }

  // A share is presented as a file with value "\\host\shareName (permissions)"
  // (ShareOutputProcessor);
  // the share name is the token after the LAST '\' once the optional " (permissions)" is dropped.
  private static String extractShareName(String value) {
    if (value == null) {
      return null;
    }
    String v = value;
    int paren = v.indexOf(" (");
    if (paren >= 0) {
      v = v.substring(0, paren);
    }
    int lastBackslash = v.lastIndexOf('\\');
    return lastBackslash < 0 || lastBackslash == v.length() - 1
        ? null
        : v.substring(lastBackslash + 1);
  }

  // username value is domain, then a backslash, then username (UsernameOutputProcessor).
  private static String extractUsernameFindingUser(String value) {
    if (value == null) {
      return null;
    }
    int i = value.lastIndexOf('\\');
    return i < 0 || i == value.length() - 1 ? null : value.substring(i + 1);
  }

  private static String extractUsernameFindingDomain(String value) {
    if (value == null) {
      return null;
    }
    int i = value.indexOf('\\');
    return i <= 0 ? null : value.substring(0, i);
  }

  private static boolean matchesIn(String value, String keyValue) {
    if (value == null || keyValue == null) {
      return false;
    }
    List<String> members =
        Arrays.stream(keyValue.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    if (members.size() > 1) {
      return members.contains(value);
    }
    return value.contains(keyValue);
  }
}
