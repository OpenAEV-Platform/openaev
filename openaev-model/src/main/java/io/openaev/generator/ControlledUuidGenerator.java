package io.openaev.generator;

import static org.hibernate.generator.EventTypeSets.INSERT_ONLY;

import io.openaev.database.model.Base;
import java.util.EnumSet;
import java.util.UUID;
import lombok.NoArgsConstructor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

/**
 * Hibernate ID generator that supports both automatic UUID generation and pre-assigned IDs.
 *
 * <p>This generator provides conditional UUID generation behavior:
 *
 * <ul>
 *   <li>If the entity already has an ID set (via {@link Base#getId()}), that ID is preserved
 *   <li>If no ID is set, a new random UUID is generated
 * </ul>
 *
 * <p>This is particularly useful for import scenarios where entities may already have assigned IDs
 * that should be retained (e.g., importing from another OpenAEV instance or restoring backups),
 * while still supporting automatic generation for newly created entities.
 *
 * <p>Usage: Apply the {@link io.openaev.annotation.ControlledUuidGeneration} annotation to entity
 * ID fields.
 *
 * @see io.openaev.annotation.ControlledUuidGeneration
 * @see Base
 */
@NoArgsConstructor
public class ControlledUuidGenerator implements BeforeExecutionGenerator {

  /**
   * Returns the event types this generator handles.
   *
   * @return {@link org.hibernate.generator.EventTypeSets#INSERT_ONLY} - only handles inserts
   */
  @Override
  public EnumSet<EventType> getEventTypes() {
    return INSERT_ONLY;
  }

  /**
   * Indicates whether this generator allows pre-assigned identifiers.
   *
   * @return {@code true} - pre-assigned IDs are preserved
   */
  @Override
  public boolean allowAssignedIdentifiers() {
    return true;
  }

  /**
   * Generates an ID for the entity, preserving any pre-assigned value.
   *
   * @param session the Hibernate session
   * @param owner the entity instance being persisted
   * @param currentValue the current ID value (unused)
   * @param eventType the event type triggering generation
   * @return the existing ID if set, otherwise a newly generated UUID string
   */
  @Override
  public Object generate(
      SharedSessionContractImplementor session,
      Object owner,
      Object currentValue,
      EventType eventType) {
    final String id;
    if (owner instanceof Base) {
      id = ((Base) owner).getId();
    } else {
      id = null;
    }

    return id != null ? id : UUID.randomUUID().toString();
  }
}
