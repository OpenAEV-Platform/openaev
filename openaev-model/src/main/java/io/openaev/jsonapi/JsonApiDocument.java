package io.openaev.jsonapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Root container for a JSON:API document.
 *
 * <p>According to the JSON:API specification, every document must contain a top-level {@code data}
 * member, and may optionally contain an {@code included} section.
 *
 * <ul>
 *   <li>{@code data} – the primary resource(s) being returned. Can be a single resource, a
 *       collection, or {@code null}.
 *   <li>{@code included} – an optional list of related resources that are side-loaded to reduce
 *       additional API calls. Each element is typically a {@code ResourceObject}.
 * </ul>
 *
 * @param <T> the type of the primary {@code data} element, often a {@code ResourceObject}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonApiDocument<T>(T data, List<Object> included) {}
