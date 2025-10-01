package io.openaev.aop.lock;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Lock {
  /** SpEL expression for the lock key Examples: "#injectId", "#user.id", "#id + ':' + #type" */
  String key();

  /** Lock resource type Allows different stripe configurations for different resources */
  LockResourceType type();

  /** Lock timeout in milliseconds (-1 for no timeout) */
  long timeout() default -1;

  /**
   * Whether to skip execution if lock cannot be acquired If false, will wait indefinitely (or until
   * timeout)
   */
  boolean skipIfLocked() default false;

  /** Custom error message if lock acquisition fails */
  String errorMessage() default "Failed to acquire lock";
}
