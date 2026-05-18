package io.openaev.utils.object;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ObjectNormalizationUtils {

    /**
     * DTO metadata fields that are never actual entity attributes. Skipped during diff computation.
     * {@code type} is a Jackson polymorphic type discriminator present in many input DTOs.
     */
    public static final Set<String> DIFF_SKIP_FIELDS = Set.of("type");

    private final ObjectMapper objectMapper;

    public JsonNode normalize(JsonNode node) {
        JsonNode normalized = normalizeValues(node);
        JsonNode cleaned = stripInsignificantValues(normalized);
        return isEffectivelyEmpty(cleaned) ? NullNode.getInstance() : cleaned;

        //TODO AUDIT: add depth level and other controllers
    }

    private JsonNode normalizeValues(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return NullNode.getInstance();
        }
        if (node.isObject()) {
            return normalizeObjectNode(node);
        }
        if (node.isArray()) {
            return normalizeArrayNode(node);
        }
        if (node.isTextual()) {
            return normalizeTextNode(node);
        }
        if (node.isNumber()) {
            return normalizeNumberNode(node);
        }
        return node;
    }

    private JsonNode normalizeObjectNode(JsonNode node) {
        ObjectNode normalized = objectMapper.createObjectNode();
        for (var entry : node.properties()) {
            String fieldName = entry.getKey();
            JsonNode normalizedValue = normalizeValues(entry.getValue());

            // Canonicalize empty collections to null for stable diffing.
            if (normalizedValue.isArray() && normalizedValue.isEmpty()) {
                normalized.set(fieldName, NullNode.getInstance());
            } else {
                normalized.set(fieldName, normalizedValue);
            }
        }
        return normalized.isEmpty() ? NullNode.getInstance() : normalized;
    }

    private JsonNode normalizeArrayNode(JsonNode node) {
        ArrayNode normalized = objectMapper.createArrayNode();
        for (JsonNode element : node) {
            normalized.add(normalizeValues(element));
        }
        return normalized.isEmpty() ? NullNode.getInstance() : normalized;
    }

    private JsonNode normalizeTextNode(JsonNode node) {
        String text = node.asText();
        return text.isBlank() ? NullNode.getInstance() : JsonNodeFactory.instance.textNode(text);
    }

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
    public JsonNode stripInsignificantValues(JsonNode node) {
        if (node == null || node.isNull()) {
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
                JsonNode value = entry.getValue();
                JsonNode cleanedValue = stripInsignificantValues(value);
                if (!isInsignificantValue(cleanedValue)) {
                    cleaned.set(fieldName, cleanedValue);
                }
            }
            return cleaned;
        }

        if (node.isArray()) {
            ArrayNode cleaned = objectMapper.createArrayNode();
            for (JsonNode element : node) {
                cleaned.add(stripInsignificantValues(element));
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
