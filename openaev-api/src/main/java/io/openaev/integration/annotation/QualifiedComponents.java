package io.openaev.integration.annotation;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QualifiedComponents {
  QualifiedComponent[] value();
}
