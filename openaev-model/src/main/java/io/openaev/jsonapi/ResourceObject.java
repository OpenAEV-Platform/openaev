package io.openaev.jsonapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Represents a resource object in a JSON API document.
 *
 * <p>According to the JSON API specification, a resource object must contain at least an {@code id}
 * and {@code type}. It may also contain {@code attributes} (the resource's data) and {@code
 * relationships} (links to related resources).
 *
 * <p>Example JSON representation:
 *
 * <pre>{@code
 * {
 *   "id": "550e8400-e29b-41d4-a716-446655440000",
 *   "type": "exercises",
 *   "attributes": {
 *     "exercise_name": "Ransomware Simulation",
 *     "exercise_status": "RUNNING"
 *   },
 *   "relationships": {
 *     "teams": {
 *       "data": [{"type": "teams", "id": "..."}]
 *     }
 *   }
 * }
 * }</pre>
 *
 * @param id the unique identifier for this resource
 * @param type the resource type (typically the table name in snake_case)
 * @param attributes a map of attribute names to values
 * @param relationships a map of relationship names to Relationship objects
 * @see JsonApiDocument
 * @see Relationship
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceObject(
    @NotBlank String id,
    @NotBlank String type,
    Map<String, Object> attributes,
    Map<String, Relationship> relationships) {}
