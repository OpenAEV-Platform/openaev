package io.openaev.annotation;

import io.openaev.database.model.Filters;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to describe whether a JPA bean property can be searched, filtered, sorted or
 * dynamically resolved by the query engine.
 *
 * <p>This annotation provides metadata to drive the construction of dynamic Spring Specifications
 * and to expose query capabilities to higher-level components (API, UI, query builders, etc.).
 *
 * <p>It can be applied to fields or getter methods.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Queryable {

  /**
   * Enables free-text search on this property (e.g. using LIKE/ILIKE operators depending on the
   * backend).
   */
  boolean searchable() default false;

  /** Allows filtering this property using supported filter operators. */
  boolean filterable() default false;

  /**
   * Indicates that the property refers to another entity stored in the database, meaning its
   * possible filter values must be retrieved dynamically. Typically used for relations (ManyToOne,
   * OneToMany, etc.).
   */
  boolean dynamicValues() default false;

  /** Allows sorting results based on this property. */
  boolean sortable() default false;

  /** Human-readable label used for documentation or UI generation. */
  String label() default "";

  /**
   * Defines the JPA path used to query this property. Follows Spring Data Specification conventions
   * (e.g. {@code "organization.id"}). Path should end by a primitive value or this can lead to
   * UnsupportedOperationException.
   */
  String path() default "";

  /**
   * Defines multiple possible JPA paths, when the property can be resolved through different
   * relationships or aliases.
   */
  String[] paths() default {};

  /**
   * Defines the complete list of operators available for filtering a collection by the decorated
   * property. If undefined, the client is responsible for guessing which operators are applicable
   * according to the decorated property type.
   */
  Filters.FilterOperator[] overrideOperators() default {};

  /**
   * Define a value here to specify a type hint for clients to treat the decorated property as this
   * type. Example: String[].class; clients parsing this hint will be encouraged to treat the value
   * of the decorated property as an array of strings.
   */
  Class clazz() default Unassigned.class;

  /** Defines the Enum source for the allowed values of the decorated property, if applicable. */
  Class refEnumClazz() default Unassigned.class;

  /**
   * Special sentinel class used to represent an "unassigned" state for {@link #clazz()} and {@link
   * #refEnumClazz()}.
   *
   * <p>We cannot use {@code Void.class} here since {@code void} is a valid type and could lead to
   * ambiguity.
   */
  class Unassigned {}
}
