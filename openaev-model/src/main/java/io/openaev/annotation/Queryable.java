package io.openaev.annotation;

import io.openaev.database.model.Filters;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Queryable {
  boolean searchable() default false;

  boolean filterable() default false;

  boolean dynamicValues() default false;

  boolean sortable() default false;

  String label() default "";

  String path() default "";

  String[] paths() default {};

  Filters.FilterOperator[] overrideOperators() default {};

  Class clazz() default Unassigned.class;

  Class refEnumClazz() default Unassigned.class;

  // use this absolutely specific class as a "null" value for class, refEnumClazz
  // don't use Void here because that is a legitimate return type
  // although there is little to justify having a queryable void method
  class Unassigned {}
}
