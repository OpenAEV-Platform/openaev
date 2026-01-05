package io.openaev.utils.mockConfig.executors;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;

@Retention(RetentionPolicy.RUNTIME)
@PropertyMapping("executor.sentinelone")
public @interface WithMockSentinelOneConfig {
  boolean enable() default false;

  String url() default "";

  String apiKey() default "";

  int apiBatchExecutionActionPagination() default 0;

  int apiRegisterInterval() default 0;

  int cleanImplantInterval() default 0;

  String accountId() default "";

  String siteId() default "";

  String groupId() default "";

  String windowsScriptId() default "";

  String unixScriptId() default "";
}
