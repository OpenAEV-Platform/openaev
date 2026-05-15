package io.openaev.utils.mockConfig;

import io.openaev.database.model.Tenant;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;

@Retention(RetentionPolicy.RUNTIME)
@PropertyMapping("openaev.xtm.opencti." + Tenant.DEFAULT_TENANT_UUID)
public @interface WithMockOpenCTIConfig {
  String url() default "";

  String apiUrl() default "";

  String token() default "";
}
