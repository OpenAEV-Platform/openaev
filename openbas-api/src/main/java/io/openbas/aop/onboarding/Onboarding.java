package io.openbas.aop.onboarding;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Onboarding {

  /** The name of the onboarding step to be marked as completed when the method is executed. */
  String step();

  /** The name of the onboarding step to be marked as completed when the method is executed. */
  boolean allUsers() default false;
}
