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

  public List<ResourceIdentifier> asMany() {
    if (data instanceof List<?> list) {
      return list.stream()
          .map(item -> MAPPER.convertValue(item, ResourceIdentifier.class))
          .toList();
    }
    return List.of();
  }

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
