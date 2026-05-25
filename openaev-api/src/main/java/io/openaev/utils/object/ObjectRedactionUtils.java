package io.openaev.utils.object;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import java.util.Set;

public class ObjectRedactionUtils {

  private ObjectRedactionUtils() {}

  private static final String REDACTED = "*** Redacted ***";

  /** Fields whose values are replaced with {@link #REDACTED} before logging. */
  private static final Set<String> SENSITIVE_FIELDS =
      Set.of("password", "token", "secret", "apikey", "api_key", "credential");

  /** Fields redacted only when the entity type is User (PII protection). */
  private static final Set<String> USER_PII_FIELDS = Set.of("name", "user_email");

  /**
   * Redacts sensitive field values in a JSON tree. Operates on a deep copy — the original is never
   * modified.
   */
  public static JsonNode redact(JsonNode node, String entityTypeName) {
    if (node == null || node.isNull()) {
      return node;
    }
    boolean isUserEntity = isUserEntityType(entityTypeName);
    return redactNode(node, isUserEntity);
  }

  /** Returns true for user-scoped entity labels like "User", "Platform User" or "Tenant User". */
  private static boolean isUserEntityType(String entityTypeName) {
    if (entityTypeName == null || entityTypeName.isBlank()) {
      return false;
    }
    String normalized = entityTypeName.trim().toLowerCase(Locale.ROOT);
    return normalized.equals("user") || normalized.endsWith(" user");
  }

  private static JsonNode redactNode(JsonNode node, boolean isUserEntity) {
    if (node == null || node.isNull()) {
      return node;
    }

    if (node.isObject()) {
      ObjectNode copy = ((ObjectNode) node).deepCopy();
      copy.properties()
          .forEach(
              entry -> {
                String key = entry.getKey();
                String fieldName = key.toLowerCase(Locale.ROOT);
                boolean containsSensitiveToken =
                    SENSITIVE_FIELDS.stream().anyMatch(fieldName::contains);

                if (containsSensitiveToken
                    || (isUserEntity && USER_PII_FIELDS.contains(fieldName))) {
                  copy.put(key, REDACTED);
                } else {
                  copy.set(key, redactNode(entry.getValue(), isUserEntity));
                }
              });
      return copy;
    }

    if (node.isArray()) {
      ArrayNode copy = ((ArrayNode) node).deepCopy();
      for (int i = 0; i < copy.size(); i++) {
        copy.set(i, redactNode(copy.get(i), isUserEntity));
      }
      return copy;
    }

    // Scalar nodes are immutable; returning as-is preserves value and avoids unnecessary copies.
    return node;
  }
}
