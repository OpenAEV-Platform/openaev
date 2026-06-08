package io.openaev.integration.annotation;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QualifiedComponent {
  String[] identifier();
}
