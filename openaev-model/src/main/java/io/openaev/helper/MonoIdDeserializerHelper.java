package io.openaev.helper;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import io.openaev.database.model.Base;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class MonoIdDeserializerHelper<T extends Base> extends JsonDeserializer<T>
    implements ContextualDeserializer {

  /**
   * Per-entity cache of the JSON property that carries the scalar id, resolved by reflection. An
   * empty {@link Optional} marks an entity with no single {@code @Id} field (e.g. an
   * {@code @EmbeddedId} composite): its object form cannot yield a scalar id, so such elements are
   * consumed and dropped rather than misread.
   */
  private static final ConcurrentHashMap<Class<?>, Optional<String>> ID_PROPERTY_CACHE =
      new ConcurrentHashMap<>();

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

    // The scalar-only form (getValueAsString) returned null on a START_OBJECT without consuming the
    // object's tokens, so the collection deserializer then read the object's first field name as
    // the next element's id - a whole-stream desync that dangled em.getReference proxies and
    // corrupted every field after the collection. Consume the current element whatever its shape
    // before resolving, so no token layout can ever misalign the stream (#7414 - same asymmetry as
    // inject_documents #7410/#7411, which has its own dedicated deserializer).
    String id = extractElementId(p);
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

  /**
   * Reads exactly one collection element and returns its scalar id, always consuming the element's
   * tokens so the stream stays aligned on the next element (the core of #7414):
   *
   * <ul>
   *   <li>a scalar value is returned verbatim (behaviour unchanged from the original helper);
   *   <li>an object (the shape {@code MultiModelSerializer} writes for these collections) is fully
   *       consumed with {@code readValueAsTree}, then its entity id property is extracted;
   *   <li>any other structured token is skipped whole and yields no id.
   * </ul>
   *
   * @return the scalar id, or {@code null} when none could be extracted (tokens already consumed)
   */
  private String extractElementId(JsonParser p) throws IOException {
    JsonToken token = p.currentToken();
    if (token == JsonToken.START_OBJECT) {
      JsonNode node = p.readValueAsTree();
      String idProperty = idPropertyName(entityClass);
      if (idProperty == null) {
        return null;
      }
      JsonNode idNode = node.get(idProperty);
      return (idNode != null && idNode.isValueNode() && !idNode.isNull()) ? idNode.asText() : null;
    }
    if (token != null && token.isStructStart()) {
      // START_ARRAY (or any other container): consume it whole so it cannot desync the stream.
      p.skipChildren();
      return null;
    }
    return p.getValueAsString();
  }

  /**
   * Resolves (and caches) the JSON property name carrying the scalar id of {@code entityClass}: the
   * {@code @JsonProperty} value of the field annotated {@link Id} anywhere in the class hierarchy,
   * falling back to the field name. Entities with no single {@code @Id} field yield {@code null}.
   */
  private static String idPropertyName(Class<?> entityClass) {
    return ID_PROPERTY_CACHE
        .computeIfAbsent(entityClass, MonoIdDeserializerHelper::resolveIdPropertyName)
        .orElse(null);
  }

  private static Optional<String> resolveIdPropertyName(Class<?> entityClass) {
    for (Class<?> current = entityClass;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        if (field.isAnnotationPresent(Id.class)) {
          JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
          if (jsonProperty != null && !jsonProperty.value().isEmpty()) {
            return Optional.of(jsonProperty.value());
          }
          return Optional.of(field.getName());
        }
      }
    }
    return Optional.empty();
  }
}
