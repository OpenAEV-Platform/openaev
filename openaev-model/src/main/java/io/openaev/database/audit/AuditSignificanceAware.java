package io.openaev.database.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Marks an entity as capable of determining whether a mutation represents a significant change
 * worthy of audit logging.
 *
 * <p>Implementations serialize the entity via {@link ObjectMapper#convertValue} and strip
 * non-significant fields (timestamps, child collections, etc.). New fields are automatically
 * included via Jackson serialization.
 */
public interface AuditSignificanceAware {

  /**
   * Returns a map representing this entity's significant state.
   *
   * <p>Implementations should serialize the entity via {@code objectMapper.convertValue(this, Map)}
   * and remove non-significant fields. This ensures new fields are automatically captured.
   *
   * @param objectMapper the ObjectMapper used to serialize the entity
   * @return a map of significant field names to their current values
   */
  Map<String, Object> significantState(ObjectMapper objectMapper);
}
