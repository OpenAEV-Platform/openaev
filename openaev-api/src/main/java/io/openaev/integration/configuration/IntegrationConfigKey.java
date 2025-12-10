package io.openaev.integration.configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IntegrationConfigKey {
  String key();
  String jsonType() default "string";
  boolean isEncrypted() default false;
  boolean isRequired() default false;
}
