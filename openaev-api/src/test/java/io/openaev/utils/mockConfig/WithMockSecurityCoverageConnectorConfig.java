package io.openaev.utils.mockConfig;

import io.openaev.database.model.Tenant;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;

@Retention(RetentionPolicy.RUNTIME)
@PropertyMapping("openaev.xtm.opencti." + Tenant.DEFAULT_TENANT_UUID)
public @interface WithMockSecurityCoverageConnectorConfig {
  boolean enable() default false;

  String url() default "";

  String token() default "";

  String listenCallbackURI() default "";
}
