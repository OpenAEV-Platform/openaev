package io.openaev.utils.mockConfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;

@Retention(RetentionPolicy.RUNTIME)
@PropertyMapping("openaev.xtm.one")
public @interface WithMockXtmOneConfig {
  String url() default "";

  String token() default "";
}
