package io.openaev.jsonapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for entities that should always be treated as <em>inner relationships</em>
 * during JSON:API import.
 *
 * <p>When applied to a class, the importer will:
 *
 * <ul>
 *   <li>Always create a new instance of the annotated entity type during import,
 *   <li>Ignore any existing persisted entity with the same identifier,
 *   <li>Preserve the provided identifier (if present) to maintain references in memory.
 * </ul>
 *
 * <p>Typical use case: entities that are tightly coupled to a parent object (e.g. configuration
 * objects embedded in a dashboard), where reusing an existing entity would not make sense.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface InnerRelationship {}
