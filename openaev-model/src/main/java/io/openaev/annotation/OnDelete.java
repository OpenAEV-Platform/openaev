package io.openaev.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(FIELD)
@Retention(RUNTIME)
public @interface OnDelete {
  OnDeleteAction action() default OnDeleteAction.SET_REFERENCE_NULL;

  String fieldName();
}
