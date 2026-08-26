package io.openaev.utils.object;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;

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
  private static final Set<Pattern> SENSITIVE_FIELDS_REGEX_TO_REDACT =
      Set.of(
          Pattern.compile(".*password.*"),
          Pattern.compile(".*secret.*"),
          Pattern.compile(".*credential.*"));

  /** Sensitive-like fields that are explicitly allowed and therefore not redacted. */
  private static final Set<Pattern> ALLOWED_SENSITIVE_FIELDS_REGEX_TO_REDACT =
      Set.of(
          Pattern.compile(".*_date"),
          Pattern.compile(".*_time"),
          Pattern.compile(".*_at"),
          Pattern.compile("^credential_id$"),
          Pattern.compile("^credential_name$"),
          Pattern.compile("^credential_type$"),
          Pattern.compile("^credential_description$"),
          Pattern.compile("^credential_auth_method$"));

  /** Fields whose values are replaced with Hash before logging. */
  private static final Set<Pattern> SENSITIVE_FIELDS_REGEX_TO_HASH =
      Set.of(
          Pattern.compile(".*token.*"),
          Pattern.compile(".*apikey.*"),
          Pattern.compile(".*api_key.*"),
          Pattern.compile("^user_pgp_key$"),
          Pattern.compile("^asset_mac_addresses$"));

  /** Sensitive-like fields that are explicitly allowed and therefore not Hashed. */
  private static final Set<Pattern> ALLOWED_SENSITIVE_FIELDS_REGEX_TO_HASH =
      ALLOWED_SENSITIVE_FIELDS_REGEX_TO_REDACT;

  /** Fields to remove only when the entity type is USER_ENTITY_TYPES (PII protection). */
  private static final Set<String> USER_PII_FIELDS_TO_REMOVE =
      Set.of(
          "name",
          "user_firstname",
          "user_lastname",
          "user_email",
          "user_phone",
          "user_phone2",
          "user_password",
          "communications_users",
          "user_lang",
          "user_country",
          "user_city");

  private static final Set<ResourceType> USER_ENTITY_TYPES =
      Set.of(ResourceType.USER, ResourceType.PLATFORM_USER, ResourceType.PLAYER);

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

  public static Object redactFieldValue(Object value, String fieldName) {
    if (value == null) {
      return null;
    }

    if (value instanceof String stringValue && !stringValue.isBlank()) {
      fieldName = fieldName.toLowerCase(Locale.ROOT);

      if (USER_PII_FIELDS_TO_REMOVE.contains(fieldName)) {
        return null;
      }

      if (shouldHash(fieldName)) {
        return hashWithSHA256(stringValue);
      }

      if (shouldRedact(fieldName)) {
        return REDACTED;
      }
    }
    return value;
  }

  private static JsonNode redactNode(JsonNode node, boolean isUserEntity) {
    if (node == null || node.isNull()) {
      return node;
    }

    if (node instanceof ObjectNode original) {
      return redactObjectNode(original, isUserEntity);
    }

    if (node instanceof ArrayNode original) {
      return redactArrayNode(original, isUserEntity);
    }

    // Scalar nodes are immutable; returning as-is preserves value and avoids unnecessary copies.
    return node;
  }

  private static ObjectNode redactObjectNode(ObjectNode original, boolean isUserEntity) {
    ObjectNode result = original.objectNode();
    original
        .properties()
        .forEach(entry -> redactProperty(result, entry.getKey(), entry.getValue(), isUserEntity));
    return result;
  }

  private static ArrayNode redactArrayNode(ArrayNode original, boolean isUserEntity) {
    ArrayNode result = original.arrayNode();
    for (JsonNode element : original) {
      result.add(redactNode(element, isUserEntity));
    }
    return result;
  }

  private static void redactProperty(
      ObjectNode result, String key, JsonNode value, boolean isUserEntity) {
    String fieldName = key.toLowerCase(Locale.ROOT);
    if (isUserEntity && USER_PII_FIELDS_TO_REMOVE.contains(fieldName)) {
      return;
    }

    if (shouldHash(fieldName)) {
      result.put(key, hashWithSHA256(toHashInput(value)));
      return;
    }

    if (shouldRedact(fieldName)) {
      result.put(key, REDACTED);
      return;
    }

    result.set(key, redactNode(value, isUserEntity));
  }

  private static boolean shouldHash(String fieldName) {
    return matchesAnyRegex(fieldName, SENSITIVE_FIELDS_REGEX_TO_HASH)
        && !matchesAnyRegex(fieldName, ALLOWED_SENSITIVE_FIELDS_REGEX_TO_HASH);
  }

  private static boolean shouldRedact(String fieldName) {
    return matchesAnyRegex(fieldName, SENSITIVE_FIELDS_REGEX_TO_REDACT)
        && !matchesAnyRegex(fieldName, ALLOWED_SENSITIVE_FIELDS_REGEX_TO_REDACT);
  }

  private static boolean matchesAnyRegex(String value, Set<Pattern> patterns) {
    return patterns.stream().anyMatch(pattern -> pattern.matcher(value).matches());
  }

  private static String toHashInput(JsonNode value) {
    if (value == null || value.isNull()) {
      return "";
    }
    return value.isTextual() ? value.textValue() : value.toString();
  }
}
