package io.openaev.engine;

import io.openaev.engine.model.EsBase;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class EngineContext {

  private ApplicationContext context;

  @Autowired
  public void setContext(ApplicationContext context) {
    this.context = context;
  }

  public <T extends EsBase> List<EsModel<T>> getModels() {
    return context.getBeansOfType(Handler.class).entrySet().stream()
        .map(
            entry -> {
              Handler<T> handler = entry.getValue();
              Class<T> clazz = resolveGenericType(handler);
              if (clazz == null) {
                throw new IllegalStateException(
                    "Cannot resolve generic type for handler " + entry.getKey());
              }
              return new EsModel<>(clazz, handler);
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
