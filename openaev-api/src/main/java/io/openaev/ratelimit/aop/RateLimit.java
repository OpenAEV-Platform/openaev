package io.openaev.ratelimit.aop;

import io.openaev.ratelimit.config.Limits;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
  long defaultRps() default Limits.DEFAULT_RPS;

  long authenticatedRps() default Limits.AUTHENTICATED_RPS;
}
