package io.openaev.database.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marks an entity as capable of determining whether a mutation represents a significant change
 * worthy of audit logging.
 *
 * <p>The default {@link #significantState(ObjectMapper)} serializes the entity via Jackson and
 * strips fields annotated with {@link AuditDiffIgnore}. New fields are automatically included
 * unless explicitly annotated.
 */
public interface AuditSignificanceAware {

  /** Cache of ignored JSON keys per class — computed once via reflection, reused thereafter. */
  Map<Class<?>, Set<String>> IGNORED_FIELDS_CACHE = new ConcurrentHashMap<>();

  /**
   * Returns a map representing this entity's significant state.
   *
   * <p>Serializes the entity via {@code objectMapper.convertValue(this, Map)} and removes fields
   * annotated with {@link AuditDiffIgnore}. New fields are automatically captured.
   *
   * @param objectMapper the ObjectMapper used to serialize the entity
   * @return a map of significant field names to their current values
   */
  default Map<String, Object> significantState(ObjectMapper objectMapper) {
    Map<String, Object> state = objectMapper.convertValue(this, new TypeReference<>() {});
    resolveIgnoredFields(this.getClass()).forEach(state::remove);
    // Sort all List<Comparable> values for stable comparison (Sets serialized as arrays have no
    // guaranteed order). Lists of complex objects (e.g. Maps) are left unsorted.
    state.replaceAll(
        (key, value) -> {
          if (value instanceof List<?> list
              && !list.isEmpty()
              && list.getFirst() instanceof Comparable) {
            return list.stream().sorted().toList();
          }
          return value;
        });
    return state;
  }

  /**
   * Resolves the set of JSON property names to exclude for the given class. The result is cached
   * per class so reflection is only performed once.
   */
  private static Set<String> resolveIgnoredFields(Class<?> clazz) {
    return IGNORED_FIELDS_CACHE.computeIfAbsent(clazz, AuditSignificanceAware::scanIgnoredFields);
  }

  /** Walks the class hierarchy and collects JSON keys of fields annotated with @AuditDiffIgnore. */
  private static Set<String> scanIgnoredFields(Class<?> clazz) {
    Set<String> ignored = new HashSet<>();
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        if (field.isAnnotationPresent(AuditDiffIgnore.class)) {
          JsonProperty jsonProp = field.getAnnotation(JsonProperty.class);
          ignored.add(jsonProp != null ? jsonProp.value() : field.getName());
        }
      }
    }
    return Set.copyOf(ignored);
  }
}
