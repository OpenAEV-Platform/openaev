package io.openbas.utils.mockConfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;

@Retention(RetentionPolicy.RUNTIME)
@PropertyMapping("openbas.xtm.hub")
public @interface WithMockXtmHubConfig {
  boolean enable() default false;

  String url() default "";

  String override_api_url() default "";
}
