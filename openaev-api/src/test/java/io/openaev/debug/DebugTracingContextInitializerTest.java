package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

@DisplayName("DebugTracingContextInitializer")
class DebugTracingContextInitializerTest {

  private final DebugTracingContextInitializer initializer = new DebugTracingContextInitializer();

  private GenericApplicationContext contextWith(Map<String, Object> props, String... profiles) {
    GenericApplicationContext ctx = new GenericApplicationContext();
    if (profiles.length > 0) {
      ctx.getEnvironment().setActiveProfiles(profiles);
    }
    ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", props));
    return ctx;
  }

  private static Map<String, Object> props(String... keyValues) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(keyValues[i], keyValues[i + 1]);
    }
    return map;
  }

  private String excludes(GenericApplicationContext ctx) {
    initializer.initialize(ctx);
    return ctx.getEnvironment().getProperty("spring.autoconfigure.exclude");
  }

  private boolean tracingExcluded(String value) {
    return value != null
        && DebugTracingContextInitializer.TRACING_AUTO_CONFIGURATIONS.stream()
            .allMatch(value::contains);
  }

  @Test
  @DisplayName("disabled debug mode excludes the tracing auto-configuration")
  void disabledExcludesTracing() {
    assertThat(tracingExcluded(excludes(contextWith(props("openaev.debug.enabled", "false")))))
        .isTrue();
  }

  @Test
  @DisplayName("refused in production excludes tracing (same gate as the barrier)")
  void refusedInProductionExcludesTracing() {
    // no non-production profile active -> isProduction == true -> barrier refuses -> tracing
    // dropped
    assertThat(tracingExcluded(excludes(contextWith(props("openaev.debug.enabled", "true")))))
        .isTrue();
  }

  @Test
  @DisplayName("allow-in-production override keeps tracing in place even in production")
  void allowInProductionKeepsTracing() {
    assertThat(
            excludes(
                contextWith(
                    props(
                        "openaev.debug.enabled", "true",
                        "openaev.debug.allow-in-production", "true"))))
        .isNull();
  }

  @Test
  @DisplayName("a non-production profile keeps tracing in place")
  void nonProductionProfileKeepsTracing() {
    assertThat(excludes(contextWith(props("openaev.debug.enabled", "true"), "test"))).isNull();
  }

  @Test
  @DisplayName("merges with an existing comma-form autoconfigure.exclude rather than dropping it")
  void mergesWithExistingCommaExclude() {
    String value =
        excludes(
            contextWith(
                props(
                    "openaev.debug.enabled", "false",
                    "spring.autoconfigure.exclude", "com.example.SomeAutoConfiguration")));
    assertThat(value).contains("com.example.SomeAutoConfiguration");
    assertThat(tracingExcluded(value)).isTrue();
  }

  @Test
  @DisplayName("merges with an existing indexed-list autoconfigure.exclude")
  void mergesWithExistingIndexedExclude() {
    String value =
        excludes(
            contextWith(
                props(
                    "openaev.debug.enabled", "false",
                    "spring.autoconfigure.exclude[0]", "com.example.IndexedAutoConfiguration")));
    assertThat(value).contains("com.example.IndexedAutoConfiguration");
    assertThat(tracingExcluded(value)).isTrue();
  }
}
