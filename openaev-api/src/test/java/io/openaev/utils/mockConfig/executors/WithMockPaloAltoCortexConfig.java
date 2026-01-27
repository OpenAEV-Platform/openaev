package io.openaev.utils.mockConfig.executors;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;

@Retention(RetentionPolicy.RUNTIME)
@PropertyMapping("executor.paloaltocortex")
public @interface WithMockPaloAltoCortexConfig {
  boolean enable() default false;

  String url() default "";

  String apiKeyId() default "";

  String apiKey() default "";

  int apiBatchExecutionActionPagination() default 0;

  int apiRegisterInterval() default 0;

  int cleanImplantInterval() default 0;

  String groupName() default "";

  String windowsScriptId() default "";

  String unixScriptId() default "";
}
