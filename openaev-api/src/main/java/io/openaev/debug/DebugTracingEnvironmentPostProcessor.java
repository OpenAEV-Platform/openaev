package io.openaev.debug;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Keeps Micrometer Tracing on the same gate as the debug-mode production barrier: tracing is
 * enabled only when debug mode actually activates (enabled AND (allow-in-production OR a
 * non-production profile)). So in the refused-in-production state no span is created on the request
 * path.
 */
public class DebugTracingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
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
