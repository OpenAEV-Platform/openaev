package io.openaev.aop.lock;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Locks {
  Lock[] value();
}
