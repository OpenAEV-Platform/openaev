package io.openaev.helper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import io.openaev.database.model.Base;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.util.Collection;
import org.springframework.stereotype.Component;

@Component
public class MonoIdDeserializerHelper<T extends Base> extends JsonDeserializer<T>
    implements ContextualDeserializer {

  private Class<? extends Base> entityClass;

  public MonoIdDeserializerHelper() {}

  private MonoIdDeserializerHelper(Class<? extends Base> entityClass) {
    this.entityClass = entityClass;
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {

    if (property == null) return this;

    JavaType type = property.getType();

    Class<? extends Base> clazz;

    // Cas simple : champ unique
    if (Base.class.isAssignableFrom(type.getRawClass())) {
      clazz = (Class<? extends Base>) type.getRawClass();
    }
    // Cas collection : récupérer le type des éléments
    else if (Collection.class.isAssignableFrom(type.getRawClass()) && type.hasGenericTypes()) {
      clazz = (Class<? extends Base>) type.getContentType().getRawClass();
    } else {
      throw new IllegalArgumentException("MonoIdSerializerHelper cannot handle type: " + type);
    }

    return new MonoIdDeserializerHelper<>(clazz);
  }

  @Override
  public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    EntityManager em =
        (EntityManager) ctxt.findInjectableValue(EntityManager.class.getName(), null, null);

    String id = p.getValueAsString();
    if (id == null || id.isBlank()) return null;

    if (em != null) {
      Object resolvedId;

      if (CompositeIdResolvableI.class.isAssignableFrom(entityClass)) {
        try {
          CompositeIdResolvableI instance =
              (CompositeIdResolvableI) entityClass.getDeclaredConstructor().newInstance();
          resolvedId = instance.resolveCompositeId(id, ctxt);
        } catch (Exception e) {
          throw new IOException(
              "Cannot resolve composite id for " + entityClass.getSimpleName(), e);
        }
      } else {
        resolvedId = id;
      }

      return (T) em.getReference(entityClass, resolvedId);
    } else {
      // fallback : stub
      try {
        T entity = (T) entityClass.getDeclaredConstructor().newInstance();
        entity.setId(id);
        return entity;
      } catch (Exception e) {
        throw new IOException("Cannot instantiate " + entityClass.getSimpleName(), e);
      }
    }
  }
}
