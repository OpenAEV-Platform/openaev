package io.openaev.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Gates Micrometer Tracing on the debug-mode production barrier. The Brave bridge creates a Tracer
 * (and the span-producing observation handlers) as soon as it is on the classpath; {@code
 * management.tracing.enabled} only governs export, not span creation. So to get genuinely zero
 * tracing cost when debug mode is off, the tracing auto-configuration itself is excluded unless
 * debug mode is active. When it is active (enabled AND allowed for the profile), it is left in
 * place and tracing runs.
 */
public class DebugTracingContextInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

  // Brave provides the real tracer and span handlers. The no-op tracer is excluded too, so that
  // no Tracer bean remains at all; otherwise the metrics layer adds a tracing-aware meter handler
  // that then fails on the missing trace context.
  static final List<String> TRACING_AUTO_CONFIGURATIONS =
      List.of(
          "org.springframework.boot.actuate.autoconfigure.tracing.BraveAutoConfiguration",
          "org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration",
          "org.springframework.boot.actuate.autoconfigure.tracing.NoopTracerAutoConfiguration");

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    ConfigurableEnvironment env = applicationContext.getEnvironment();
    if (DebugEnabledCondition.isDebugActive(env)) {
      return; // debug active: leave tracing auto-configuration in place so spans are produced
    }
    // Read any existing exclusions with Binder so both the comma form and the indexed list form
    // (spring.autoconfigure.exclude[0]=...) are merged rather than clobbered.
    List<String> excludes =
        new ArrayList<>(
            Binder.get(env)
                .bind("spring.autoconfigure.exclude", Bindable.listOf(String.class))
                .orElseGet(List::of));
    TRACING_AUTO_CONFIGURATIONS.stream().filter(c -> !excludes.contains(c)).forEach(excludes::add);
    env.getPropertySources()
        .addFirst(
            new MapPropertySource(
                "openaev-debug-tracing",
                Map.of("spring.autoconfigure.exclude", String.join(",", excludes))));
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE; // after profiles are resolved
  }
}
