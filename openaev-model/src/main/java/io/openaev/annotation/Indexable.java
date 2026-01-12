package io.openaev.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that marks an entity class as indexable in the search engine.
 *
 * <p>Entities annotated with {@code @Indexable} will be automatically indexed in Elasticsearch or
 * OpenSearch for full-text search and filtering capabilities.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Entity
 * @Indexable(index = "scenarios", label = "Scenario", ref = "scenario")
 * public class Scenario implements Base {
 *     // ...
 * }
 * }</pre>
 *
 * @see EsQueryable
 * @see Queryable
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Indexable {

  /**
   * The name of the search engine index where documents of this entity type will be stored.
   *
   * <p>This should be a lowercase, snake_case identifier (e.g., "scenarios", "attack_patterns").
   *
   * @return the index name
   */
  String index();

  /**
   * A human-readable label for this entity type, used in UI components and documentation.
   *
   * @return the display label
   */
  String label();

  /**
   * An optional reference identifier used for cross-referencing or linking purposes.
   *
   * <p>When specified, this value can be used to establish relationships between indexed documents
   * or to provide a stable identifier for external integrations.
   *
   * @return the reference identifier, or an empty string if not applicable
   */
  String ref() default "";
}
