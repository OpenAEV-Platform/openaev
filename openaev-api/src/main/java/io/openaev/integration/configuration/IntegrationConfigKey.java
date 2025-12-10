package io.openaev.integration.configuration;

import io.openaev.database.model.CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT;
import io.openaev.database.model.CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IntegrationConfigKey {
  String key();

  CONNECTOR_CONFIGURATION_TYPE jsonType() default CONNECTOR_CONFIGURATION_TYPE.STRING;

  CONNECTOR_CONFIGURATION_FORMAT valueFormat() default CONNECTOR_CONFIGURATION_FORMAT.DEFAULT;

  boolean isRequired() default false;

  String description() default "";

  Class<? extends Enum> refEnumClass() default Unassigned.class;

  enum Unassigned {}
}
