package io.openaev.integration.annotation;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(QualifiedComponents.class)
public @interface QualifiedComponent {
  String[] identifier();
}
