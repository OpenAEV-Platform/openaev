package io.openaev.database.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Base;
import io.openaev.utils.reflection.FieldUtils;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marks an entity as capable of determining whether a mutation represents a significant change
 * worthy of audit logging.
 *
 * <p>The default {@link #significantState(ObjectMapper)} serializes the entity via Jackson and
 * strips fields annotated with {@link AuditStateIgnore}. New fields are automatically included
 * unless explicitly annotated.
 *
 * <p>Collections of {@link AuditStateCapturable} entities are automatically resolved: each element
 * delegates to its own {@code significantState()}, sorted by entity ID for stable comparison.
 */
public interface AuditStateCapturable {

  /** Cache of ignored JSON keys per class — computed once via reflection, reused thereafter. */
  Map<Class<?>, Set<String>> IGNORED_FIELDS_CACHE = new ConcurrentHashMap<>();

  /**
   * Returns a map representing this entity's significant state.
   *
   * <p>Serializes the entity via {@code objectMapper.convertValue(this, Map)} and removes fields
   * annotated with {@link AuditStateIgnore}. New fields are automatically captured.
   *
   * <p>For fields that are collections of {@link AuditStateCapturable} entities, the raw Jackson
   * serialization is replaced by each element's own {@code significantState()}, sorted by ID.
   *
   * @param objectMapper the ObjectMapper used to serialize the entity
   * @return a map of significant field names to their current values
   */
  default Map<String, Object> significantState(ObjectMapper objectMapper) {
    Map<String, Object> state = objectMapper.convertValue(this, new TypeReference<>() {});
    resolveIgnoredFields(this.getClass()).forEach(state::remove);

    // Replace collections of AuditStateCapturable with each element's significantState()
    for (Field field : FieldUtils.getAllFields(this.getClass())) {
      if (FieldUtils.isStaticOrTransient(field)
          || !Collection.class.isAssignableFrom(field.getType())) {
        continue;
      }
      Object raw = FieldUtils.getField(this, field);
      if (raw instanceof Collection<?> col
          && !col.isEmpty()
          && col.iterator().next() instanceof AuditStateCapturable) {
        String key = FieldUtils.resolveFieldJsonName(field);
        state.put(
            key,
            col.stream()
                .map(AuditStateCapturable.class::cast)
                .sorted(
                    Comparator.comparing(
                        e -> e instanceof Base b ? b.getId() : String.valueOf(e.hashCode())))
                .map(e -> e.significantState(objectMapper))
                .toList());
      }
    }

    // Sort all List<Comparable> values for stable comparison
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
    return IGNORED_FIELDS_CACHE.computeIfAbsent(clazz, AuditStateCapturable::scanIgnoredFields);
  }

  /**
   * Walks the class hierarchy and collects JSON keys of fields annotated with @AuditStateIgnore.
   */
  private static Set<String> scanIgnoredFields(Class<?> clazz) {
    Set<String> ignored = new HashSet<>();
    for (Field field : FieldUtils.getAllFields(clazz)) {
      if (field.isAnnotationPresent(AuditStateIgnore.class)) {
        ignored.add(FieldUtils.resolveFieldJsonName(field));
      }
    }
    return Set.copyOf(ignored);
  }
}
