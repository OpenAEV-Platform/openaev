package io.openaev.utils.object;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.ResourceType;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class ObjectRedactionUtils {

  private ObjectRedactionUtils() {}

  private static final String REDACTED = "*** Redacted ***";

  /** Fields whose values are replaced with {@link #REDACTED} before logging. */
  private static final Set<Pattern> SENSITIVE_FIELDS_REGEX =
      Set.of(
          Pattern.compile(".*password.*"),
          Pattern.compile(".*token.*"),
          Pattern.compile(".*secret.*"),
          Pattern.compile(".*apikey.*"),
          Pattern.compile(".*api_key.*"),
          Pattern.compile(".*credential.*"));

  /** Sensitive-like fields that are explicitly allowed and therefore not redacted. */
  private static final Set<Pattern> ALLOWED_SENSITIVE_FIELDS_REGEX =
      Set.of(Pattern.compile(".*_date"), Pattern.compile(".*_time"), Pattern.compile(".*_at"));

  /** Fields redacted only when the entity type is User (PII protection). */
  private static final Set<String> USER_PII_FIELDS =
      Set.of(
          "name",
          "user_firstname",
          "user_lastname",
          "user_email",
          "user_phone",
          "user_phone2",
          "user_pgp_key",
          "user_password",
          "communications_users");

  /** Fields to hide only when the entity type is User (PII protection). */
  private static final Set<String> USER_PII_FIELDS_TO_HIDE = Set.of("user_pgp_key");

  private static final Set<ResourceType> USER_ENTITY_TYPES =
      Set.of(ResourceType.USER, ResourceType.PLATFORM_USER);

  /**
   * Redacts sensitive field values in a JSON tree. Operates on a deep copy — the original is never
   * modified.
   */
  public static JsonNode redact(JsonNode node, ResourceType resourceType) {
    if (node == null || node.isNull()) {
      return node;
    }
    boolean isUserEntity = resourceType != null && USER_ENTITY_TYPES.contains(resourceType);
    return redactNode(node, isUserEntity);
  }

  private static JsonNode redactNode(JsonNode node, boolean isUserEntity) {
    if (node == null || node.isNull()) {
      return node;
    }

    if (node instanceof ObjectNode original) {
      ObjectNode result = original.objectNode();
      original
          .properties()
          .forEach(
              entry -> {
                String key = entry.getKey();
                String fieldName = key.toLowerCase(Locale.ROOT);
                boolean redact =
                    (matchesAnyRegex(fieldName, SENSITIVE_FIELDS_REGEX)
                            && !matchesAnyRegex(fieldName, ALLOWED_SENSITIVE_FIELDS_REGEX))
                        || (isUserEntity && USER_PII_FIELDS.contains(fieldName));

                if (!redact) {
                  result.set(key, redactNode(entry.getValue(), isUserEntity));
                } else if (!isUserEntity || !USER_PII_FIELDS_TO_HIDE.contains(fieldName)) {
                  result.put(key, REDACTED);
                }
              });
      return result;
    }

    if (node instanceof ArrayNode original) {
      ArrayNode result = original.arrayNode();
      for (JsonNode element : original) {
        result.add(redactNode(element, isUserEntity));
      }
      return result;
    }

    // Scalar nodes are immutable; returning as-is preserves value and avoids unnecessary copies.
    return node;
  }

  private static boolean matchesAnyRegex(String value, Set<Pattern> patterns) {
    return patterns.stream().anyMatch(pattern -> pattern.matcher(value).matches());
  }
}
