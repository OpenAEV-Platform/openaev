package io.openaev.annotation;

import static java.lang.annotation.ElementType.FIELD;

import io.openaev.generator.ControlledUuidGenerator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.hibernate.annotations.IdGeneratorType;

/**
 * Annotation that marks a field for controlled UUID generation.
 *
 * <p>When applied to an entity's ID field, this annotation enables conditional UUID generation
 * behavior: if a value is already set, it will be preserved; otherwise, a new UUID will be
 * generated automatically.
 *
 * <p>This is particularly useful for import scenarios where entities may already have assigned IDs
 * that should be retained, while still allowing automatic generation for new entities.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Id
 * @ControlledUuidGeneration
 * @Column(name = "entity_id")
 * private String id;
 * }</pre>
 *
 * @see ControlledUuidGenerator
 */
@IdGeneratorType(ControlledUuidGenerator.class)
@Target({FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ControlledUuidGeneration {}
