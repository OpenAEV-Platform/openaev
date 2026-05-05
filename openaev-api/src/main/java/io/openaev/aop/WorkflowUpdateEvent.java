package io.openaev.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WorkflowUpdateEvent {

  /**
   * The SPEL to fetch the inject ID from the request params Setting this is mutually exclusive with
   * setting the other fields from this annotation (control is enforced at runtime in the aspect
   * code)
   */
  String injectId() default "";

  /**
   * The SPEL to fetch a collection of inject IDs from the request params. Mutually exclusive with
   * injectId and expectationIds.
   */
  String injectIds() default "";

  /**
   * The SPEL to fetch a singular or a list of expectation IDs from the request params From these
   * expectations IDs, we will then fetch the associated inject IDs and finally, the step IDs
   * Setting this is mutually exclusive with setting the other fields from this annotation (control
   * is enforced at runtime in the aspect code)
   */
  String expectationIds() default "";
}
