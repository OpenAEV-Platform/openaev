package io.openaev.utils.object;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ObjectDiffUtils {

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

  public DiffResult computeDiff(JsonNode oldSnapshot, JsonNode newInput, String entityType) {
    JsonNode normalizedOld = objectNormalizationUtils.normalize(oldSnapshot, entityType);
    JsonNode normalizedNew = objectNormalizationUtils.normalize(newInput, entityType);

    if (ObjectNormalizationUtils.isEffectivelyEmpty(normalizedNew)) {
      return new DiffResult(null, null);
    }

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
      if (shouldSkipField(fieldName, newValue)) {
        continue;
      }

      if (handleRelationField(changedNew, changedOld, fieldName, oldValue, newValue)) {
        continue;
      }

      if (handleNestedObjectField(changedNew, changedOld, fieldName, oldValue, newValue, depth)) {
        continue;
      }

      handlePrimitiveOrCollectionField(
          changedNew, changedOld, fieldName, oldValue, newValue, depth);
    }

    if (changedNew.isEmpty()) {
      return new DiffResult(null, null);
    }
    return new DiffResult(changedNew, changedOld.isEmpty() ? null : changedOld);
  }

  private static boolean shouldSkipField(String fieldName, JsonNode newValue) {
    // Null input means "not provided" in this diff flow.
    if (newValue == null || newValue.isNull()) {
      return true;
    }
    // DTO metadata fields are not entity attributes.
    return ObjectNormalizationPolicy.DIFF_SKIP_FIELDS.contains(fieldName);
  }

  private static boolean handleRelationField(
      ObjectNode changedNew,
      ObjectNode changedOld,
      String fieldName,
      JsonNode oldValue,
      JsonNode newValue) {
    if (oldValue == null || !oldValue.isObject() || newValue.isObject()) {
      return false;
    }

    JsonNode oldId = extractIdFromRelation(oldValue);
    if (oldId == null) {
      return false;
    }
    if (oldId.equals(newValue)) {
      return true;
    }

    changedNew.set(fieldName, newValue);
    changedOld.set(fieldName, oldId);
    return true;
  }

  private boolean handleNestedObjectField(
      ObjectNode changedNew,
      ObjectNode changedOld,
      String fieldName,
      JsonNode oldValue,
      JsonNode newValue,
      int depth) {
    if (oldValue == null || !oldValue.isObject() || !newValue.isObject()) {
      return false;
    }

    DiffResult nested = computeDiff((ObjectNode) oldValue, (ObjectNode) newValue, depth + 1);
    if (nested.newValues() != null) {
      changedNew.set(fieldName, nested.newValues());
      if (nested.oldValues() != null) {
        changedOld.set(fieldName, nested.oldValues());
      }
    }
    return true;
  }

  private static void handlePrimitiveOrCollectionField(
      ObjectNode changedNew,
      ObjectNode changedOld,
      String fieldName,
      JsonNode oldValue,
      JsonNode newValue,
      int depth) {
    if (oldValue == null || !semanticEquals(oldValue, newValue, depth)) {
      changedNew.set(fieldName, newValue);
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
