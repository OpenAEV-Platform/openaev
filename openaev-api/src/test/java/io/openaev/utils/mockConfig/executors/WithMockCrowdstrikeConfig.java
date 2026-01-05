package io.openaev.utils.mockConfig.executors;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;

@Retention(RetentionPolicy.RUNTIME)
@PropertyMapping("executor.crowdstrike")
public @interface WithMockCrowdstrikeConfig {
  boolean enable() default false;

  String apiUrl() default "";

  String clientId() default "";

  String clientSecret() default "";

  String hostGroup() default "";

  String windowsScriptName() default "";

  String unixScriptName() default "";

  int apiRegisterInterval() default 0;
}
