package io.openaev.engine;

import io.openaev.engine.model.EsBase;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EngineContext {

  private final List<Handler<?>> handlers;

  // Constructor injection: a handler in a bean cycle fails startup instead of being skipped.
  public EngineContext(List<Handler<?>> handlers) {
    this.handlers = handlers;
  }

  @SuppressWarnings("unchecked")
  public <T extends EsBase> List<EsModel<T>> getModels() {
    return handlers.stream()
        .map(
            handler -> {
              Handler<T> typedHandler = (Handler<T>) handler;
              Class<T> clazz = resolveGenericType(typedHandler);
              if (clazz == null) {
                throw new IllegalStateException(
                    "Cannot resolve generic type for handler " + handler.getClass().getName());
              }
              return new EsModel<>(clazz, typedHandler);
            })
        .toList();
  }

  @SuppressWarnings("rawtypes")
  private <T extends EsBase> Class<T> resolveGenericType(Handler handler) {
    for (Type iface : handler.getClass().getGenericInterfaces()) {
      if (iface instanceof ParameterizedType pType) {
        if (pType.getRawType().equals(Handler.class)) {
          Type actualType = pType.getActualTypeArguments()[0];
          if (actualType instanceof Class<?> cls && EsBase.class.isAssignableFrom(cls)) {
            return (Class<T>) cls;
          }
        }
      }
    }
    return null;
  }
}
