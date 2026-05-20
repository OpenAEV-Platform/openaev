package io.openaev.utils.object;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.utils.SystemLoadGuardUtils;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ObjectNormalizationUtils {

  // Backward-compatible aliases for existing callers (e.g. ObjectDiffUtils).
  public static final int DEPTH_LEVEL = ObjectNormalizationPolicy.DEPTH_LEVEL;
  public static final Set<String> DIFF_SKIP_FIELDS = ObjectNormalizationPolicy.DIFF_SKIP_FIELDS;

  private final ObjectMapper objectMapper;
  private final SystemLoadGuardUtils systemLoadGuardUtils;
  private final ObjectNormalizationPolicy objectNormalizationPolicy;

  private record SerializedNode(JsonNode node, String json, int sizeInBytes) {}

  public JsonNode normalize(JsonNode node) {
    return normalize(node, "default");
  }

  /** Normalizes, redacts and size-limits an input payload according to entity policy. */
  public JsonNode normalize(JsonNode node, String entityType) {
    if (shouldSkipAllNormalization()) {
      return node;
    }

    if (shouldSkipFullNormalization()) {
      // Keep deterministic and safe output even under high load.
      JsonNode schemaOnly = applySchemaRules(node, entityType, 0);
      return enforceMaxEventSize(schemaOnly, entityType);
    }

    JsonNode schemaNormalized = applySchemaRules(node, entityType, 0);
    JsonNode normalized = normalizeValues(schemaNormalized, 0);
    JsonNode cleaned = stripInsignificantValues(normalized, 0);
    JsonNode result = isEffectivelyEmpty(cleaned) ? NullNode.getInstance() : cleaned;

    return enforceMaxEventSize(result, entityType);
  }

  /** Enforces maximum event size using truncation and allowlist fallback before final envelope. */
  private JsonNode enforceMaxEventSize(JsonNode node, String entityType) {
    if (node == null || node.isNull() || objectNormalizationPolicy.maxEventSizeBytes() <= 0) {
      return node;
    }

    SerializedNode initial = serializeNode(node);
    if (initial.sizeInBytes() <= objectNormalizationPolicy.maxEventSizeBytes()) {
      return node;
    }

    JsonNode truncatedStrings = truncateStrings(node, 0);
    SerializedNode truncatedStringsSerialized = serializeNode(truncatedStrings);
    if (truncatedStringsSerialized.sizeInBytes() <= objectNormalizationPolicy.maxEventSizeBytes()) {
      return truncatedStrings;
    }

    JsonNode allowlisted = applyAllowlistOnly(truncatedStrings, entityType, 0);
    SerializedNode allowlistedSerialized = serializeNode(allowlisted);
    if (allowlistedSerialized.sizeInBytes() <= objectNormalizationPolicy.maxEventSizeBytes()) {
      return allowlisted;
    }

    return buildTruncatedEnvelope(
        initial.json(),
        initial.sizeInBytes(),
        entityType,
        objectNormalizationPolicy.maxEventSizeBytes());
  }

  /** Creates a compact fallback payload when the normalized event still exceeds size limits. */
  private JsonNode buildTruncatedEnvelope(
      String serializedNode, int originalSize, String entityType, int maxSizeBytes) {
    ObjectNode truncated = objectMapper.createObjectNode();
    int previewLimit =
        Math.max(
            0,
            Math.min(objectNormalizationPolicy.truncationPreviewBytes(), serializedNode.length()));

    truncated.put("truncated", true);
    truncated.put("entity_type", objectNormalizationPolicy.normalizeEntityType(entityType));
    truncated.put("original_size_bytes", originalSize);
    truncated.put("max_size_bytes", maxSizeBytes);
    truncated.put("preview", serializedNode.substring(0, previewLimit));
    return truncated;
  }

  /** Truncates oversized text values recursively while preserving JSON structure. */
  private JsonNode truncateStrings(JsonNode node, int depth) {
    if (node == null || node.isNull() || depth >= DEPTH_LEVEL) {
      return node;
    }

    if (node.isTextual()) {
      String value = node.asText();
      int maxBytes = objectNormalizationPolicy.maxStringBytes();
      int valueBytes = value.getBytes(StandardCharsets.UTF_8).length;
      if (valueBytes <= maxBytes) {
        return node;
      }

      String suffix = objectNormalizationPolicy.truncatedSuffix();
      int suffixBytes = suffix.getBytes(StandardCharsets.UTF_8).length;
      if (suffixBytes >= maxBytes) {
        return JsonNodeFactory.instance.textNode(truncateUtf8ToMaxBytes(suffix, maxBytes));
      }

      int contentBudget = maxBytes - suffixBytes;
      String truncatedValue = truncateUtf8ToMaxBytes(value, contentBudget) + suffix;
      return JsonNodeFactory.instance.textNode(truncatedValue);
    }

    if (node.isObject()) {
      ObjectNode truncated = objectMapper.createObjectNode();
      for (var entry : node.properties()) {
        truncated.set(entry.getKey(), truncateStrings(entry.getValue(), depth + 1));
      }
      return truncated;
    }

    if (node.isArray()) {
      ArrayNode truncated = objectMapper.createArrayNode();
      for (JsonNode element : node) {
        truncated.add(truncateStrings(element, depth + 1));
      }
      return truncated;
    }

    return node;
  }

  /** Applies schema-level allowlist/denylist redaction rules recursively. */
  private JsonNode applySchemaRules(JsonNode node, String entityType, int depth) {
    if (node == null || node.isNull() || depth >= DEPTH_LEVEL) {
      return node;
    }

    if (node.isObject()) {
      return applyObjectSchemaRules((ObjectNode) node, entityType, depth);
    }

    if (node.isArray()) {
      ArrayNode normalized = objectMapper.createArrayNode();
      for (JsonNode element : node) {
        normalized.add(applySchemaRules(element, entityType, depth + 1));
      }
      return normalized;
    }

    return node;
  }

  /** Applies policy rules to object fields (allowlist filtering + sensitive field redaction). */
  private ObjectNode applyObjectSchemaRules(ObjectNode source, String entityType, int depth) {
    ObjectNode normalized = objectMapper.createObjectNode();
    Set<String> allowlist = objectNormalizationPolicy.allowlistForEntity(entityType);
    Set<String> denylist = objectNormalizationPolicy.denylistForEntity(entityType);

    for (var entry : source.properties()) {
      String fieldName = entry.getKey();

      if (allowlist != null && !allowlist.contains(fieldName)) {
        continue;
      }

      if (objectNormalizationPolicy.isGloballyDeniedField(fieldName)
          || denylist.contains(fieldName)) {
        normalized.put(fieldName, objectNormalizationPolicy.redactedValue());
        continue;
      }

      normalized.set(fieldName, applySchemaRules(entry.getValue(), entityType, depth + 1));
    }
    return normalized;
  }

  /** Keeps only allowlisted fields as a last-resort size reduction step. */
  private JsonNode applyAllowlistOnly(JsonNode node, String entityType, int depth) {
    if (node == null || node.isNull() || depth >= DEPTH_LEVEL || !node.isObject()) {
      return node;
    }

    Set<String> allowlist = objectNormalizationPolicy.allowlistForEntity(entityType);
    if (allowlist == null) {
      return node;
    }

    ObjectNode reduced = objectMapper.createObjectNode();
    ObjectNode source = (ObjectNode) node;
    for (String field : allowlist) {
      JsonNode value = source.get(field);
      if (value != null) {
        reduced.set(field, applyAllowlistOnly(value, entityType, depth + 1));
      }
    }
    return reduced;
  }

  /** Serializes a node once and stores both JSON text and UTF-8 size for reuse. */
  private SerializedNode serializeNode(JsonNode node) {
    String json = safeSerialize(node);
    return new SerializedNode(node, json, json.getBytes(StandardCharsets.UTF_8).length);
  }

  /** Safe JSON serialization used by size enforcement; falls back to String value on failure. */
  private String safeSerialize(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (Exception ex) {
      log.debug(
          "[AUDIT] Failed to serialize normalized node for size estimation: {}", ex.getMessage());
      return String.valueOf(node);
    }
  }

  /** Truncates a string to a UTF-8 byte budget without splitting multibyte code points. */
  private String truncateUtf8ToMaxBytes(String value, int maxBytes) {
    if (maxBytes <= 0 || value.isEmpty()) {
      return "";
    }

    int byteCount = 0;
    int endIndex = 0;
    int index = 0;
    while (index < value.length()) {
      int codePoint = value.codePointAt(index);
      int codePointBytes = utf8Bytes(codePoint);
      if (byteCount + codePointBytes > maxBytes) {
        break;
      }
      byteCount += codePointBytes;
      index += Character.charCount(codePoint);
      endIndex = index;
    }
    return value.substring(0, endIndex);
  }

  /** Returns the number of bytes required to encode the given Unicode code point in UTF-8. */
  private int utf8Bytes(int codePoint) {
    if (codePoint <= 0x7F) {
      return 1;
    }
    if (codePoint <= 0x7FF) {
      return 2;
    }
    if (codePoint <= 0xFFFF) {
      return 3;
    }
    return 4;
  }

  /** Returns true when normalization is globally disabled by policy. */
  public boolean shouldSkipAllNormalization() {
    return objectNormalizationPolicy.skipAllNormalization();
  }

  /** Guards full normalization steps when runtime load is above configured thresholds. */
  public boolean shouldSkipFullNormalization() {
    if (!objectNormalizationPolicy.skipOnHighLoad()) {
      return false;
    }

    if (systemLoadGuardUtils.isHeapUsageHigh(objectNormalizationPolicy.maxHeapUsageRatio())) {
      log.debug("[AUDIT] Skipping normalization due to high heap usage");
      return true;
    }

    if (systemLoadGuardUtils.isProcessCpuLoadHigh(objectNormalizationPolicy.maxProcessCpuLoad())) {
      log.debug("[AUDIT] Skipping normalization due to high process CPU load");
      return true;
    }

    return false;
  }

  /** Canonicalizes scalar/container values recursively (depth-limited). */
  private JsonNode normalizeValues(JsonNode node, int depth) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return NullNode.getInstance();
    }
    // At max depth stop recursing into containers; keep scalars as-is
    if (depth >= DEPTH_LEVEL) {
      // return node.isContainerNode() ? NullNode.getInstance() : node;
      return node;
    }
    if (node.isObject()) {
      return normalizeObjectNode(node, depth);
    }
    if (node.isArray()) {
      return normalizeArrayNode(node, depth);
    }
    if (node.isTextual()) {
      return normalizeTextNode(node);
    }
    if (node.isNumber()) {
      return normalizeNumberNode(node);
    }
    return node;
  }

  /** Normalizes object fields recursively and canonicalizes empty arrays to null. */
  private JsonNode normalizeObjectNode(JsonNode node, int depth) {
    ObjectNode normalized = objectMapper.createObjectNode();
    for (var entry : node.properties()) {
      String fieldName = entry.getKey();
      JsonNode normalizedValue = normalizeValues(entry.getValue(), depth + 1);

      // Canonicalize empty collections to null for stable diffing.
      if (normalizedValue.isArray() && normalizedValue.isEmpty()) {
        normalized.set(fieldName, NullNode.getInstance());
      } else {
        normalized.set(fieldName, normalizedValue);
      }
    }
    return normalized.isEmpty() ? NullNode.getInstance() : normalized;
  }

  /** Normalizes array elements recursively. */
  private JsonNode normalizeArrayNode(JsonNode node, int depth) {
    ArrayNode normalized = objectMapper.createArrayNode();
    for (JsonNode element : node) {
      normalized.add(normalizeValues(element, depth + 1));
    }
    return normalized.isEmpty() ? NullNode.getInstance() : normalized;
  }

  /** Converts blank text values to null. */
  private JsonNode normalizeTextNode(JsonNode node) {
    String text = node.asText();
    return text.isBlank() ? NullNode.getInstance() : JsonNodeFactory.instance.textNode(text);
  }

  /** Canonicalizes numeric representation by stripping trailing zeros. */
  private JsonNode normalizeNumberNode(JsonNode node) {
    BigDecimal stripped = node.decimalValue().stripTrailingZeros();
    if (stripped.scale() <= 0) {
      BigInteger integerValue = stripped.toBigIntegerExact();
      return JsonNodeFactory.instance.numberNode(integerValue);
    }
    return JsonNodeFactory.instance.numberNode(stripped);
  }

  /**
   * Recursively strips insignificant values (nulls, empty strings, empty arrays, false booleans)
   * from a {@link JsonNode} tree. Also removes fields listed in {@link #DIFF_SKIP_FIELDS}. This
   * keeps create-event audit entries concise — only meaningful, non-default values are logged.
   *
   * <p>For arrays, each element is cleaned recursively (but elements are never removed, to preserve
   * positional semantics). For objects, fields whose cleaned value is insignificant are dropped.
   *
   * @return a new, cleaned copy of the tree — the original is never mutated
   */
  private JsonNode stripInsignificantValues(JsonNode node, int depth) {
    if (node == null || node.isNull()) {
      return node;
    }
    // At max depth stop recursing into containers; keep scalars as-is
    if (depth >= DEPTH_LEVEL) {
      // return node.isContainerNode() ? NullNode.getInstance() : node;
      return node;
    }

    if (node.isObject()) {
      ObjectNode cleaned = objectMapper.createObjectNode();
      for (var entry : node.properties()) {
        String fieldName = entry.getKey();
        // Skip DTO metadata fields (same set as diff computation)
        if (DIFF_SKIP_FIELDS.contains(fieldName)) {
          continue;
        }
        JsonNode cleanedValue = stripInsignificantValues(entry.getValue(), depth + 1);
        if (!isInsignificantValue(cleanedValue)) {
          cleaned.set(fieldName, cleanedValue);
        }
      }
      return cleaned;
    }

    if (node.isArray()) {
      ArrayNode cleaned = objectMapper.createArrayNode();
      for (JsonNode element : node) {
        cleaned.add(stripInsignificantValues(element, depth + 1));
      }
      return cleaned;
    }

    // Scalars (string, number, boolean) — return as-is; caller decides significance
    return node;
  }

  /**
   * Returns {@code true} when a value is insignificant for audit-logging purposes: {@code null},
   * empty strings, empty arrays, empty objects, or zero-valued numbers that represent "not set".
   * Used by {@link #stripInsignificantValues} to remove noise from create-event inputs.
   */
  public static boolean isInsignificantValue(JsonNode node) {
    if (isEffectivelyEmpty(node)) {
      return true;
    }
    // Empty string (e.g. inject_description: "")
    if (node.isTextual() && node.asText().isEmpty()) {
      return true;
    }
    // Boolean false (default value for most boolean fields)
    return node.isBoolean() && !node.asBoolean();
  }

  /**
   * Returns {@code true} when the node is semantically empty: {@code null}, a Jackson {@code
   * NullNode}, an empty array, or an empty object.
   */
  public static boolean isEffectivelyEmpty(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return true;
    }
    if (node.isArray() && node.isEmpty()) {
      return true;
    }
    return node.isObject() && node.isEmpty();
  }
}
