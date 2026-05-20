package io.openaev.utils.object;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ObjectDiffUtils {

  @Value("${openaev.audit.diff.skip:false}")
  private boolean skipDiffComputation;

  private final ObjectMapper objectMapper;
  private final ObjectNormalizationUtils objectNormalizationUtils;

  public record DiffResult(JsonNode newValues, JsonNode oldValues) {}

  /**
   * Computes a diff between the old entity snapshot and the new input DTO. Returns only the fields
   * that actually changed. Handles JPA relation fields gracefully: when the input sends a scalar ID
   * but the old snapshot has a full object, the object is flattened to its ID for comparison.
   */
  public DiffResult computeDiff(JsonNode oldSnapshot, JsonNode newInput) {
    return computeDiff(oldSnapshot, newInput, "default");
  }

  /** Computes diff after applying normalization policy for the provided entity type. */
  public DiffResult computeDiff(JsonNode oldSnapshot, JsonNode newInput, String entityType) {
    // Global switch to bypass diff generation.
    if (skipDiffComputation) {
      return new DiffResult(null, null);
    }

    // Normalize both inputs with the same entity policy to avoid representation-only deltas.
    JsonNode normalizedOld = objectNormalizationUtils.normalize(oldSnapshot, entityType);
    JsonNode normalizedNew = objectNormalizationUtils.normalize(newInput, entityType);

    // No meaningful content to compare after normalization.
    if (ObjectNormalizationUtils.isEffectivelyEmpty(normalizedNew)) {
      return new DiffResult(null, null);
    }

    // Scalar/array payload mode: compare as whole values instead of object fields.
    if (!normalizedNew.isObject()) {
      if (ObjectNormalizationUtils.isEffectivelyEmpty(normalizedOld)
          || !semanticEquals(normalizedOld, normalizedNew, 0)) {
        return new DiffResult(normalizedNew, normalizedOld);
      }
      return new DiffResult(null, null);
    }

    ObjectNode oldObject =
        normalizedOld != null && normalizedOld.isObject()
            ? (ObjectNode) normalizedOld
            : objectMapper.createObjectNode();
    return computeDiff(oldObject, (ObjectNode) normalizedNew, 0);
  }

  /** Recursive object diff used for field-level comparison of nested structures. */
  private DiffResult computeDiff(ObjectNode oldSnapshot, ObjectNode newInput, int depth) {
    ObjectNode changedNew = objectMapper.createObjectNode();
    ObjectNode changedOld = objectMapper.createObjectNode();

    if (depth >= ObjectNormalizationPolicy.DEPTH_LEVEL) {
      return new DiffResult(null, null);
    }

    for (Map.Entry<String, JsonNode> entry : newInput.properties()) {
      String fieldName = entry.getKey();
      JsonNode newValue = entry.getValue();
      JsonNode oldValue = oldSnapshot.get(fieldName);

      // 1) Skip metadata and null input fields.
      if (shouldSkipField(fieldName, newValue)) {
        continue;
      }

      // 2) Handle relation object (old) vs relation ID (new).
      if (handleRelationField(changedNew, changedOld, fieldName, oldValue, newValue)) {
        continue;
      }

      // 3) Recurse into nested objects for field-level delta.
      if (handleNestedObjectField(changedNew, changedOld, fieldName, oldValue, newValue, depth)) {
        continue;
      }

      // 4) Fallback for primitives/arrays.
      handlePrimitiveOrCollectionField(
          changedNew, changedOld, fieldName, oldValue, newValue, depth);
    }

    if (changedNew.isEmpty()) {
      return new DiffResult(null, null);
    }
    return new DiffResult(changedNew, changedOld.isEmpty() ? null : changedOld);
  }

  /** Skips fields that are not meaningful for diffing (null input or DTO metadata). */
  private static boolean shouldSkipField(String fieldName, JsonNode newValue) {
    // Null input means "not provided" in this diff flow.
    if (newValue == null || newValue.isNull()) {
      return true;
    }
    // DTO metadata fields are not entity attributes.
    return ObjectNormalizationPolicy.DIFF_SKIP_FIELDS.contains(fieldName);
  }

  /** Handles relation fields where old snapshot is an object and new input is an ID. */
  private static boolean handleRelationField(
      ObjectNode changedNew,
      ObjectNode changedOld,
      String fieldName,
      JsonNode oldValue,
      JsonNode newValue) {
    // Relation path applies only when old value is an object and new value is a scalar ID.
    if (oldValue == null || !oldValue.isObject() || newValue.isObject()) {
      return false;
    }

    // Flatten old relation object to its ID for semantic comparison.
    JsonNode oldId = extractIdFromRelation(oldValue);
    // If no ID can be resolved, let caller continue with other comparison strategies.
    if (oldId == null) {
      return false;
    }
    // Same relation target => no diff for this field.
    if (oldId.equals(newValue)) {
      return true;
    }

    // Relation changed: store new scalar ID and flattened previous ID.
    changedNew.set(fieldName, newValue);
    changedOld.set(fieldName, oldId);
    return true;
  }

  /** Handles nested object comparison by delegating to recursive diff. */
  private boolean handleNestedObjectField(
      ObjectNode changedNew,
      ObjectNode changedOld,
      String fieldName,
      JsonNode oldValue,
      JsonNode newValue,
      int depth) {
    // Only handle this branch when both sides are objects.
    if (oldValue == null || !oldValue.isObject() || !newValue.isObject()) {
      return false;
    }

    // Recurse to produce a field-level nested delta instead of replacing the whole object.
    DiffResult nested = computeDiff((ObjectNode) oldValue, (ObjectNode) newValue, depth + 1);
    if (nested.newValues() != null) {
      // Keep only changed nested fields in both new/old payloads.
      changedNew.set(fieldName, nested.newValues());
      if (nested.oldValues() != null) {
        changedOld.set(fieldName, nested.oldValues());
      }
    }
    return true;
  }

  /** Handles primitive/array/object fallback comparison using semantic equality. */
  private static void handlePrimitiveOrCollectionField(
      ObjectNode changedNew,
      ObjectNode changedOld,
      String fieldName,
      JsonNode oldValue,
      JsonNode newValue,
      int depth) {
    // Fallback path for primitives/arrays or mixed types using semantic comparison.
    if (oldValue == null || !semanticEquals(oldValue, newValue, depth)) {
      changedNew.set(fieldName, newValue);
      // Old value is included only when present in the previous snapshot.
      if (oldValue != null) {
        changedOld.set(fieldName, oldValue);
      }
    }
  }

  /**
   * Deep semantic equality for two {@link JsonNode} values. Unlike {@link JsonNode#equals}, this
   * method:
   *
   * <ul>
   *   <li>Treats numeric values as equal when their {@code doubleValue()} matches (so {@code 100}
   *       == {@code 100.0}).
   *   <li>Considers {@code null} and an empty array {@code []} as equivalent (common when JPA
   *       serialises an empty collection as {@code []} but the DTO omits it or sends {@code null}).
   *   <li>Considers {@code null} and an empty object {@code {}} as equivalent.
   *   <li>Recurses into objects and arrays applying the same rules at every nesting level.
   * </ul>
   */
  private static boolean semanticEquals(JsonNode a, JsonNode b, int depth) {
    // At max depth treat containers as equal to avoid false positives on unprocessed subtrees
    if (depth >= ObjectNormalizationPolicy.DEPTH_LEVEL) {
      return true;
    }

    // Normalise null/missing nodes
    boolean aEmpty = ObjectNormalizationUtils.isEffectivelyEmpty(a);
    boolean bEmpty = ObjectNormalizationUtils.isEffectivelyEmpty(b);
    if (aEmpty && bEmpty) {
      return true;
    }
    if (aEmpty || bEmpty) {
      return false;
    }

    // Both are numeric — compare by numeric value (handles int vs double, e.g. 100 vs 100.0)
    if (a.isNumber() && b.isNumber()) {
      return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
    }

    // Both are objects — compare field-by-field
    if (a.isObject() && b.isObject()) {
      ObjectNode objA = (ObjectNode) a;
      ObjectNode objB = (ObjectNode) b;
      // Check all fields in A exist and match in B
      for (var entry : objA.properties()) {
        if (!semanticEquals(entry.getValue(), objB.get(entry.getKey()), depth + 1)) {
          return false;
        }
      }
      // Check B doesn't have extra non-empty fields
      for (var entry : objB.properties()) {
        if (objA.get(entry.getKey()) == null
            && !ObjectNormalizationUtils.isEffectivelyEmpty(entry.getValue())) {
          return false;
        }
      }
      return true;
    }

    // Both are arrays — compare element-by-element
    if (a.isArray() && b.isArray()) {
      ArrayNode arrA = (ArrayNode) a;
      ArrayNode arrB = (ArrayNode) b;
      if (arrA.size() != arrB.size()) {
        return false;
      }
      for (int i = 0; i < arrA.size(); i++) {
        if (!semanticEquals(arrA.get(i), arrB.get(i), depth + 1)) {
          return false;
        }
      }
      return true;
    }

    // Fallback: delegate to Jackson's strict equals (covers strings, booleans, etc.)
    return a.equals(b);
  }

  /**
   * Extracts the ID from a serialized JPA relation object. Looks for common ID field patterns:
   * {@code *_id} fields (e.g. {@code injector_contract_id}, {@code payload_id}) or plain {@code
   * id}.
   */
  private static JsonNode extractIdFromRelation(JsonNode objectNode) {
    // First try fields ending with "_id" (JPA naming convention: injector_contract_id, etc.)
    for (Map.Entry<String, JsonNode> field : objectNode.properties()) {
      if (field.getKey().endsWith("_id") && field.getValue().isTextual()) {
        return field.getValue();
      }
    }
    // Fallback: plain "id"
    JsonNode idNode = objectNode.get("id");
    if (idNode != null && idNode.isTextual()) {
      return idNode;
    }
    return null;
  }
}
