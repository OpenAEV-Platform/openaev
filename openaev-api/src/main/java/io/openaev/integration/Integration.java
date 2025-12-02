package io.openaev.integration;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.utils.reflection.FieldUtils;
import java.lang.reflect.Field;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class Integration {
  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstance connectorInstance;

  public abstract void start() throws Exception;

  public abstract void stop();

  public void initialise() throws Exception {
    if (ConnectorInstance.REQUESTED_STATUS_TYPE.starting.equals(
        this.connectorInstance.getRequestedStatus())) {
      this.start();
    } else {
      this.stop();
    }
  }

  public <T> List<T> requestComponent(ComponentRequest request, Class<T> componentType)
      throws IllegalAccessException {
    List<Field> candidates =
        componentRequestEngine.validate(
            request,
            FieldUtils.getAllFields(this.getClass()).stream()
                .filter(f -> componentType.isAssignableFrom(f.getType()))
                .toList());

    if (candidates.size() > 1) {
      throw new IllegalStateException("Too many components qualify for request.");
    }

    return candidates.stream().map(candidate -> (T) FieldUtils.getField(this, candidate)).toList();
  }
}
