package io.openaev.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to provide Elasticsearch-specific query metadata for entity properties.
 *
 * <p>This annotation complements {@link Queryable} by providing additional hints for Elasticsearch
 * indexing and querying behavior. It can be applied to fields or getter methods.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Queryable(searchable = true, filterable = true)
 * @EsQueryable(keyword = true)
 * private String name;
 * }</pre>
 *
 * @see Queryable
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface EsQueryable {

  /**
   * Indicates whether this field should be indexed as a keyword type in Elasticsearch.
   *
   * <p>When {@code true}, the field will be indexed as an exact-match keyword field, which is
   * suitable for filtering, sorting, and aggregations. When {@code false} (default), the field may
   * be analyzed for full-text search.
   *
   * @return {@code true} if the field should be indexed as a keyword, {@code false} otherwise
   */
  boolean keyword() default false;
}
