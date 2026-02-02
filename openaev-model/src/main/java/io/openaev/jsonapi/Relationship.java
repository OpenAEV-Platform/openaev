package io.openaev.jsonapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * JSON:API {@code relationship} representation.
 *
 * <p>A relationship in JSON:API describes the link between a resource and one or more related
 * resources. This record wraps the {@code data} field of a relationship, which can be:
 *
 * <ul>
 *   <li>a single {@link ResourceIdentifier}, for one-to-one relations,
 *   <li>a list of {@link ResourceIdentifier}, for one-to-many relations.
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Relationship(@NotNull Object data) {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Converts the relationship data to a list of resource identifiers.
   *
   * <p>Use this method for to-many relationships (e.g., one exercise has many teams).
   *
   * @return the list of resource identifiers, or an empty list if data is not a list
   */
  public List<ResourceIdentifier> asMany() {
    if (data instanceof List<?> list) {
      return list.stream()
          .map(item -> MAPPER.convertValue(item, ResourceIdentifier.class))
          .toList();
    }
    return List.of();
  }

  /**
   * Converts the relationship data to a single resource identifier.
   *
   * <p>Use this method for to-one relationships (e.g., an inject belongs to one exercise).
   *
   * @return the resource identifier, or {@code null} if data cannot be converted
   */
  public ResourceIdentifier asOne() {
    if (data instanceof ResourceIdentifier resourceIdentifier) {
      return resourceIdentifier;
    }
    if (data instanceof java.util.Map<?, ?> map) {
      return MAPPER.convertValue(map, ResourceIdentifier.class);
    }
    return null;
  }
}
