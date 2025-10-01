package io.openaev.jsonapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field of an entity as an "optional include" in JSON:API exports.
 *
 * <p>By default, all relationships are included during export. When a field is annotated with
 * {@code @IncludeOption}, its inclusion can be dynamically controlled by {@link IncludeOptions}
 * provided at runtime.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface IncludeOption {
  String key() default "";
}
