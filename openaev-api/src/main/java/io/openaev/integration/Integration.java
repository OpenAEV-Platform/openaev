package io.openaev.integration;

import io.openaev.utils.reflection.FieldUtils;
import java.lang.reflect.Field;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class Integration {
  private final ComponentRequestEngine componentRequestEngine;

  public abstract void start() throws Exception;

  public abstract void stop();

  public <T> T requestComponent(ComponentRequest request, Class<T> componentType)
      throws IllegalAccessException {
    List<Field> candidates =
        componentRequestEngine.validate(
            request,
            FieldUtils.getAllFields(this.getClass()).stream()
                .filter(f -> componentType.isAssignableFrom(f.getType()))
                .toList());

    if (candidates.isEmpty()) {
      throw new UnsupportedOperationException("No component qualify for request.");
    } else if (candidates.size() > 1) {
      throw new IllegalStateException("Too many components qualify for request.");
    }

    return (T) FieldUtils.getField(this, candidates.getFirst());
  }
}
