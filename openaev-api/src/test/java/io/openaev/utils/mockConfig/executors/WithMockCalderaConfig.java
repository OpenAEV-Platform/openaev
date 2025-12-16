package io.openaev.utils.mockConfig.executors;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;

@Retention(RetentionPolicy.RUNTIME)
@PropertyMapping("executor.caldera")
public @interface WithMockCalderaConfig {
  boolean enable() default false;

  String url() default "";

  String publicUrl() default "";

  String apiKey() default "";
}
