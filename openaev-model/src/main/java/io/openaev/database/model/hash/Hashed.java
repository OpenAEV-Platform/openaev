package io.openaev.database.model.hash;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enters the content hash, under the field's JSON name unless {@link #key()} is set. On a method,
 * the method takes no argument and a null result enters nothing. A document, or a {@link
 * FileReference}, enters by the hash of its file.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Hashed {

  Order order() default Order.VALUE;

  String key() default "";

  /** On a relation: enter this property of the related object instead of the object itself. */
  String property() default "";

  enum Order {
    /** As is: a scalar, or a list kept in its order. */
    VALUE,
    /** Order does not matter: sorted. On a map, each value is sorted. */
    SET,
    /** Objects in their order, each hashed by its own fields. */
    LIST,
    /** Objects whose order does not matter, each hashed by its own fields, sorted. */
    OBJECTS
  }
}
