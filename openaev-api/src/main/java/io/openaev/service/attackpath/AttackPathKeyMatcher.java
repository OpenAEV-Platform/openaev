package io.openaev.service.attackpath;

import io.openaev.database.model.attackpath.projection.AttackPathFindingRow;
import io.openaev.service.attackpath.dto.ConsumedFindingKeyDTO;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Matches a produced finding against a consumed finding key, mirroring the front's finding matcher
 * so the producer the backend resolves is the finding the front would match. The key type is
 * reconciled to the finding-type vocabulary, then the operator is applied: {@code EQ} (value
 * equals), {@code IN} (comma-separated membership, single token falls back to substring), and
 * {@code IS_NOT_NULL} (presence). Every other operator matches nothing. Primitive keys only;
 * reaching into a complex finding's sub-field is deferred.
 */
public final class AttackPathKeyMatcher {

  // The complex sub-field keys whose vocabulary differs from the finding type; every other key type
  // maps to a finding type 1:1 (identity).
  private static final Map<String, String> KEYTYPE_TO_FINDING_TYPE =
      Map.of("share_name", "share", "password", "credentials");

  private AttackPathKeyMatcher() {}

  /** The finding type a consumed key type reconciles to (identity when there is no mapping). */
  public static String reconciledType(String keyType) {
    // Map.of() rejects a null lookup (NPE), and a null key type never matches a finding anyway.
    if (keyType == null) {
      return null;
    }
    return KEYTYPE_TO_FINDING_TYPE.getOrDefault(keyType, keyType);
  }

  public static boolean matches(AttackPathFindingRow finding, ConsumedFindingKeyDTO key) {
    if (finding == null || key == null) {
      return false;
    }
    String reconciledType = reconciledType(key.keyType());
    if (reconciledType == null || !reconciledType.equals(finding.type())) {
      return false;
    }
    String value = finding.value();
    return switch (key.operator() == null ? "" : key.operator()) {
      case "IS_NOT_NULL" -> value != null && !value.isBlank();
      case "EQ" -> key.value() != null && key.value().equals(value);
      case "IN" -> matchesIn(value, key.value());
      default -> false;
    };
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
