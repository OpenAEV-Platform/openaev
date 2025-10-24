package io.openaev.utils.mockConfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;

@Retention(RetentionPolicy.RUNTIME)
@PropertyMapping("openaev.xtm.opencti")
public @interface WithMockSecurityCoverageConnectorConfig {
  boolean enable() default false;

  String url() default "";

  String token() default "";

  String listenCallbackURI() default "";
}
