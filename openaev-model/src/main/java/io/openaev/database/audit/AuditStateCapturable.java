package io.openaev.database.audit;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.openaev.database.model.Base;
import io.openaev.utils.reflection.FieldUtils;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marks an entity as capable of determining whether a mutation represents a significant change
 * worthy of audit logging.
 *
 * <p>The default {@link #significantState(ObjectMapper)} serializes the entity via Jackson using a
 * filter that excludes fields annotated with {@link AuditStateIgnore} at serialization time. New
 * fields are automatically included unless explicitly annotated.
 *
 * <p>Collections of {@link AuditStateCapturable} entities are automatically resolved: each element
 * delegates to its own {@code significantState()}, sorted by entity ID for stable comparison.
 */
@JsonFilter(AuditStateCapturable.AUDIT_STATE_FILTER)
public interface AuditStateCapturable {

  /** Filter name used by the mix-in to activate property filtering. */
  String AUDIT_STATE_FILTER = "auditStateFilter";

  /**
   * Cache of the filtered ObjectMapper — one copy per source ObjectMapper instance. Typically there
   * is a single Spring-managed ObjectMapper, so this cache holds exactly one entry.
   */
  Map<ObjectMapper, ObjectMapper> FILTERED_MAPPER_CACHE = new ConcurrentHashMap<>();

  /**
   * Returns a map representing this entity's significant state.
   *
   * <p>Uses a Jackson filter to exclude {@link AuditStateIgnore}-annotated fields during
   * serialization (avoiding expensive serialization of ignored collections). New fields are
   * automatically captured.
   *
   * <p>For fields that are collections of {@link AuditStateCapturable} entities, the raw Jackson
   * serialization is replaced by each element's own {@code significantState()}, sorted by ID.
   *
   * @param objectMapper the ObjectMapper used to serialize the entity
   * @return a map of significant field names to their current values
   */
  default Map<String, Object> significantState(ObjectMapper objectMapper) {
    ObjectMapper filtered = getFilteredMapper(objectMapper);
    Map<String, Object> state = filtered.convertValue(this, new TypeReference<>() {});

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
   * Returns a cached copy of the given ObjectMapper configured with the audit state filter. The
   * filter excludes fields annotated with {@link AuditStateIgnore} at serialization time.
   */
  private static ObjectMapper getFilteredMapper(ObjectMapper source) {
    return FILTERED_MAPPER_CACHE.computeIfAbsent(
        source,
        om -> {
          FilterProvider filterProvider =
              new SimpleFilterProvider()
                  .addFilter(AUDIT_STATE_FILTER, new AuditStatePropertyFilter());
          return om.copy().setFilterProvider(filterProvider);
        });
  }
}
