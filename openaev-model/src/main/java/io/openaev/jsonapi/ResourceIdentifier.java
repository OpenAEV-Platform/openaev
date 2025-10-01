package io.openaev.jsonapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Lightweight JSON:API resource identifier object.
 *
 * <p>According to the JSON:API specification, a resource identifier is used to uniquely reference a
 * resource within relationships. It always contains:
 *
 * <ul>
 *   <li>{@code id} – the unique identifier of the resource
 *   <li>{@code type} – the type of the resource (e.g., {@code "widgets"})
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceIdentifier(@NotBlank String id, @NotBlank String type) {}
