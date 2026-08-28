package io.openaev.utils.object;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.ResourceType;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectRedactionUtils {

  private ObjectRedactionUtils() {}

  private static final Logger log = LoggerFactory.getLogger(ObjectRedactionUtils.class);

  private static final String REDACTED = "[REDACTED]";
  private static final Set<String> WARNING_KEYS = ConcurrentHashMap.newKeySet();

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
    return redactNode(node, isUserEntity, resourceType);
  }

  public static Object redactFieldValue(Object value, String fieldName) {
    if (value == null) {
      return null;
    }

    if (value instanceof String stringValue && !stringValue.isBlank()) {
      fieldName = fieldName.toLowerCase(Locale.ROOT);

      if (USER_PII_FIELDS_TO_REMOVE.contains(fieldName)) {
        warnMissingAuditAnnotation(fieldName, "@AuditLogIgnore", null, "remove_pii");
        return null;
      }

      if (shouldHash(fieldName)) {
        if (looksLikeSha256(stringValue)) {
          return stringValue;
        }
        warnMissingAuditAnnotation(fieldName, "@AuditLogHash", null, "hash");
        return hashWithSHA256(stringValue);
      }

      if (shouldRedact(fieldName)) {
        if (REDACTED.equals(stringValue)) {
          return stringValue;
        }
        warnMissingAuditAnnotation(fieldName, "@AuditLogRedact", null, "redact");
        return REDACTED;
      }
    }
    return value;
  }

  private static JsonNode redactNode(
      JsonNode node, boolean isUserEntity, ResourceType resourceType) {
    if (node == null || node.isNull()) {
      return node;
    }

    if (node instanceof ObjectNode original) {
      return redactObjectNode(original, isUserEntity, resourceType);
    }

    if (node instanceof ArrayNode original) {
      return redactArrayNode(original, isUserEntity, resourceType);
    }

    // Scalar nodes are immutable; returning as-is preserves value and avoids unnecessary copies.
    return node;
  }

  private static ObjectNode redactObjectNode(
      ObjectNode original, boolean isUserEntity, ResourceType resourceType) {
    ObjectNode result = original.objectNode();
    original
        .properties()
        .forEach(
            entry ->
                redactProperty(
                    result, entry.getKey(), entry.getValue(), isUserEntity, resourceType));
    return result;
  }

  private static ArrayNode redactArrayNode(
      ArrayNode original, boolean isUserEntity, ResourceType resourceType) {
    ArrayNode result = original.arrayNode();
    for (JsonNode element : original) {
      result.add(redactNode(element, isUserEntity, resourceType));
    }
    return result;
  }

  private static void redactProperty(
      ObjectNode result,
      String key,
      JsonNode value,
      boolean isUserEntity,
      ResourceType resourceType) {
    String fieldName = key.toLowerCase(Locale.ROOT);
    if (isUserEntity && USER_PII_FIELDS_TO_REMOVE.contains(fieldName)) {
      warnMissingAuditAnnotation(fieldName, "@AuditLogIgnore", resourceType, "remove_pii");
      return;
    }

    if (shouldHash(fieldName)) {
      if (isAlreadySha256(value)) {
        result.set(key, value);
        return;
      }
      warnMissingAuditAnnotation(fieldName, "@AuditLogHash", resourceType, "hash");
      result.put(key, hashWithSHA256(toHashInput(value)));
      return;
    }

    if (shouldRedact(fieldName)) {
      if (isAlreadyRedacted(value)) {
        result.set(key, value);
        return;
      }
      warnMissingAuditAnnotation(fieldName, "@AuditLogRedact", resourceType, "redact");
      result.put(key, REDACTED);
      return;
    }

    result.set(key, redactNode(value, isUserEntity, resourceType));
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

  private static boolean isAlreadyRedacted(JsonNode value) {
    return value != null && value.isTextual() && REDACTED.equals(value.textValue());
  }

  private static boolean isAlreadySha256(JsonNode value) {
    return value != null && value.isTextual() && looksLikeSha256(value.textValue());
  }

  private static boolean looksLikeSha256(String value) {
    return value != null && value.matches("^[a-fA-F0-9]{64}$");
  }

  private static void warnMissingAuditAnnotation(
      String fieldName, String expectedAnnotation, ResourceType resourceType, String operation) {
    String warningKey = fieldName + "|" + operation + "|" + resourceType;
    if (WARNING_KEYS.add(warningKey)) {
      log.warn(
          "[AUDIT] ObjectRedactionUtils fallback '{}' applied on field '{}' (resourceType='{}'). Consider adding {} on the corresponding model/DTO field.",
          operation,
          fieldName,
          resourceType,
          expectedAnnotation);
    }
  }
}
