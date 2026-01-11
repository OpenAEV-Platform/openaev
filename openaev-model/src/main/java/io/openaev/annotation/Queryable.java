package io.openaev.annotation;

import io.openaev.database.model.Filters;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that defines query capabilities for JPA entity properties.
 *
 * <p>This annotation provides metadata to drive the construction of dynamic Spring Specifications
 * and to expose query capabilities to higher-level components (API, UI, query builders, etc.).
 *
 * <p>Properties annotated with {@code @Queryable} can be configured for:
 *
 * <ul>
 *   <li>Free-text search (LIKE/ILIKE operations)
 *   <li>Filtering with various operators
 *   <li>Sorting in query results
 *   <li>Dynamic value resolution for relationships
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Queryable(searchable = true, filterable = true, sortable = true)
 * @Column(name = "scenario_name")
 * private String name;
 * }</pre>
 *
 * @see EsQueryable
 * @see Indexable
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Queryable {

  /**
   * Enables free-text search on this property.
   *
   * <p>When {@code true}, the property can be searched using LIKE/ILIKE operators depending on the
   * database backend.
   *
   * @return {@code true} if the property is searchable, {@code false} otherwise
   */
  boolean searchable() default false;

  /**
   * Enables filtering on this property using supported filter operators.
   *
   * @return {@code true} if the property is filterable, {@code false} otherwise
   */
  boolean filterable() default false;

  /**
   * Indicates that the property's filter values must be retrieved dynamically from the database.
   *
   * <p>Typically used for relationships (ManyToOne, OneToMany, etc.) where possible values are
   * stored as separate entities.
   *
   * @return {@code true} if values should be resolved dynamically, {@code false} otherwise
   */
  boolean dynamicValues() default false;

  /**
   * Enables sorting results based on this property.
   *
   * @return {@code true} if the property is sortable, {@code false} otherwise
   */
  boolean sortable() default false;

  /**
   * Human-readable label for this property, used in documentation or UI generation.
   *
   * @return the display label, or an empty string if not specified
   */
  String label() default "";

  /**
   * Defines the JPA path used to query this property.
   *
   * <p>Follows Spring Data Specification conventions (e.g., {@code "organization.id"}). The path
   * should end with a primitive value; otherwise, this may lead to {@link
   * UnsupportedOperationException}.
   *
   * @return the JPA path, or an empty string to use the field name
   */
  String path() default "";

  /**
   * Defines multiple possible JPA paths for this property.
   *
   * <p>Useful when the property can be resolved through different relationships or aliases.
   *
   * @return an array of JPA paths, or an empty array if not applicable
   */
  String[] paths() default {};

  /**
   * Specifies the complete list of operators available for filtering by this property.
   *
   * <p>If not specified, clients are responsible for determining applicable operators based on the
   * property type.
   *
   * @return an array of allowed filter operators, or an empty array to use defaults
   */
  Filters.FilterOperator[] overrideOperators() default {};

  /**
   * Provides a type hint for clients processing this property.
   *
   * <p>Example: {@code String[].class} indicates clients should treat the property value as an
   * array of strings.
   *
   * @return the type hint class, or {@link Unassigned} if not specified
   */
  Class clazz() default Unassigned.class;

  /**
   * Specifies the enum class that defines allowed values for this property.
   *
   * <p>Used when the property's valid values are constrained to a specific enumeration.
   *
   * @return the enum class, or {@link Unassigned} if not applicable
   */
  Class refEnumClazz() default Unassigned.class;

  /**
   * Sentinel class representing an "unassigned" state for type hint attributes.
   *
   * <p>This class is used as a default value for {@link #clazz()} and {@link #refEnumClazz()} to
   * indicate that no type hint has been specified. {@code Void.class} cannot be used since {@code
   * void} is a valid type that could cause ambiguity.
   */
  class Unassigned {}
}
