package io.openaev.debug;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Aligns Micrometer Tracing with the debug-mode production barrier, but only when debug mode is
 * requested ({@code openaev.debug.enabled=true}): tracing is forced on when the mode actually
 * activates and off when it is refused in production, so no span is created on the refused path.
 * When debug mode is not requested, {@code management.tracing.enabled} is left untouched, so
 * tracing stays available to normal configuration.
 */
public class DebugTracingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
    if (!env.getProperty("openaev.debug.enabled", Boolean.class, false)) {
      return;
    }
    boolean tracing = DebugEnabledCondition.isDebugActive(env);
    env.getPropertySources()
        .addFirst(
            new MapPropertySource(
                "openaev-debug-tracing",
                Map.of("management.tracing.enabled", String.valueOf(tracing))));
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE; // after profiles are resolved
  }
}
