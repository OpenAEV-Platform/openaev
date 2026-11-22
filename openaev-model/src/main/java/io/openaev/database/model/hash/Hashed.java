package io.openaev.database.model.hash;

import io.openaev.utils.catalog.SemanticVersion;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;

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

  /**
   * What a change of this member makes of a new local version of its payload. As the catalogue's
   * own rule, a member no rule names needs a major version.
   */
  Change change() default Change.MAJOR;

  enum Change {
    /** What it needs or offers to run: injects may have to adapt. Also any item removed. */
    MAJOR(SemanticVersion::nextMajor),
    /** What it runs or checks. */
    MINOR(SemanticVersion::nextMinor),
    /** Text and descriptive metadata. */
    PATCH(SemanticVersion::nextPatch);

    private final Function<SemanticVersion, String> next;

    Change(Function<SemanticVersion, String> next) {
      this.next = next;
    }

    public String next(SemanticVersion newest) {
      return next.apply(newest);
    }
  }

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
