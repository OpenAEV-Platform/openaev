package io.openaev.utils.object;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

public class ObjectRedactionUtils {

    private static final String REDACTED = "*** Redacted ***";

    /** Fields whose values are replaced with {@link #REDACTED} before logging. */
    private static final Set<String> SENSITIVE_FIELDS =
            Set.of("password", "token", "secret", "newpassword", "apikey", "credential");

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
        ObjectNode copy = node.deepCopy();
        boolean isUserEntity = "User".equalsIgnoreCase(entityTypeName);
        copy.properties()
                .forEach(
                        entry -> {
                            String fieldName = entry.getKey().toLowerCase();
                            if (SENSITIVE_FIELDS.contains(fieldName)) {
                                copy.put(entry.getKey(), REDACTED);
                            } else if (isUserEntity && USER_PII_FIELDS.contains(fieldName)) {
                                copy.put(entry.getKey(), REDACTED);
                            }
                        });
        return copy;
    }
}
