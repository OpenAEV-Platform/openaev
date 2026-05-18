package io.openaev.utils.object;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ObjectDiffUtils {

    private final ObjectMapper objectMapper;

    public record DiffResult(JsonNode newValues, JsonNode oldValues) {}

    /**
     * Computes a diff between the old entity snapshot and the new input DTO. Returns only the fields
     * that actually changed. Handles JPA relation fields gracefully: when the input sends a scalar ID
     * but the old snapshot has a full object, the object is flattened to its ID for comparison.
     */
    public DiffResult computeDiff(JsonNode oldSnapshot, JsonNode newInput) {
        //TODO: add depth level and other controllers

        ObjectNode changedNew = objectMapper.createObjectNode();
        ObjectNode changedOld = objectMapper.createObjectNode();

        for (Map.Entry<String, JsonNode> entry : newInput.properties()) {
            String fieldName = entry.getKey();
            JsonNode newValue = entry.getValue();
            JsonNode oldValue = oldSnapshot.get(fieldName);

            // Skip null input values — in REST convention, null means "not provided" (the service
            // ignores it), not "clear this field". Including them causes false positives for
            // server-managed fields (e.g. inject_injector, resolved from the contract server-side).
            if (newValue == null || newValue.isNull()) {
                continue;
            }

            // Skip DTO metadata fields that are never actual entity attributes
            if (ObjectNormalizationUtils.DIFF_SKIP_FIELDS.contains(fieldName)) {
                continue;
            }

            // Handle JPA relation fields: input sends a scalar ID, old snapshot has a full object.
            // Flatten the old object to its ID for comparison and storage.
            if (oldValue != null && oldValue.isObject() && !newValue.isObject()) {
                JsonNode oldId = extractIdFromRelation(oldValue);
                if (oldId != null) {
                    if (oldId.equals(newValue)) {
                        continue; // Same ID — field didn't change
                    }
                    // Different ID — record the flattened old value
                    changedNew.set(fieldName, newValue);
                    changedOld.set(fieldName, oldId);
                    continue;
                }
            }

            // Standard comparison for non-relation fields — uses semantic equality that normalises
            // numeric types (100 == 100.0), treats null ≈ empty arrays, and recurses into nested
            // objects/arrays so that insignificant serialisation differences are ignored.
            if (oldValue == null || !semanticEquals(oldValue, newValue)) {
                changedNew.set(fieldName, newValue);
                if (oldValue != null) {
                    changedOld.set(fieldName, oldValue);
                }
            }
        }

        if (changedNew.isEmpty()) {
            return new DiffResult(null, null);
        }
        return new DiffResult(changedNew, changedOld.isEmpty() ? null : changedOld);
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
    private static boolean semanticEquals(JsonNode a, JsonNode b) {
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
                if (!semanticEquals(entry.getValue(), objB.get(entry.getKey()))) {
                    return false;
                }
            }
            // Check B doesn't have extra non-empty fields
            for (var entry : objB.properties()) {
                if (objA.get(entry.getKey()) == null && !ObjectNormalizationUtils.isEffectivelyEmpty(entry.getValue())) {
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
                if (!semanticEquals(arrA.get(i), arrB.get(i))) {
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
