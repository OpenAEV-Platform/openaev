package io.openaev.database.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;

/**
 * Marks an entity as capable of determining whether a mutation represents a significant change
 * worthy of audit logging.
 *
 * <p>The default {@link #significantState(ObjectMapper)} serializes the entity via Jackson and
 * strips fields listed in {@link #nonSignificantFields()}. Implementations only need to override
 * {@code nonSignificantFields()} to exclude entity-specific timestamps or computed fields.
 */
public interface AuditSignificanceAware {

  /**
   * Returns the set of JSON property names to exclude from significance comparison.
   *
   * <p>Default is empty — all serialized fields are considered significant. Override (or use Lombok
   * {@code @Getter} on a field named {@code nonSignificantFields}) to exclude entity-specific
   * timestamps, computed values, or parent references.
   *
   * @return field names to strip from the serialized state
   */
  default Set<String> getNonSignificantFields() {
    return Set.of();
  }

  /**
   * Returns a map representing this entity's significant state.
   *
   * <p>Serializes the entity via {@code objectMapper.convertValue(this, Map)} and removes fields
   * listed in {@link #getNonSignificantFields()}. New fields are automatically captured.
   *
   * @param objectMapper the ObjectMapper used to serialize the entity
   * @return a map of significant field names to their current values
   */
  default Map<String, Object> significantState(ObjectMapper objectMapper) {
    Map<String, Object> state = objectMapper.convertValue(this, new TypeReference<>() {});
    getNonSignificantFields().forEach(state::remove);
    return state;
  }
}
