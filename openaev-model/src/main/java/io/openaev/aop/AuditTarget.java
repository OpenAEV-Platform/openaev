package io.openaev.aop;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Describes one entity type affected by a {@code @Modifying} query, along with the method parameter
 * that holds its ID(s).
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditTarget {

  /** The entity class affected. */
  Class<?> entityType();

  /** The name of the method parameter containing the ID(s) of affected entities. */
  String paramName();
}
