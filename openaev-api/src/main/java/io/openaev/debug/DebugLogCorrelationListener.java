package io.openaev.debug;

import java.util.Map;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Removes the log correlation slot ({@code [traceId spanId]}) when debug mode is off. Spring Boot
 * adds a default {@code logging.pattern.correlation} as soon as {@code
 * io.micrometer.tracing.Tracer} is on the classpath, so without this the logs carry an empty {@code
 * [ ]} slot even when nothing is traced. When debug mode is active the pattern is left to Spring
 * Boot, so the traceId shows in the logs (the point of debug mode).
 *
 * <p>It runs on {@link ApplicationEnvironmentPreparedEvent}, before the logging system reads the
 * pattern, and ordered ahead of Spring Boot's {@code LoggingApplicationListener}. It is registered
 * on the {@code SpringApplicationBuilder} in {@code App} (an environment post-processor would not
 * do, as its {@code .imports} registration is not honoured and {@code spring.factories} is
 * generated here).
 */
public class DebugLogCorrelationListener
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    suppressCorrelationWhenDebugOff(event.getEnvironment());
  }

  void suppressCorrelationWhenDebugOff(ConfigurableEnvironment env) {
    if (DebugEnabledCondition.isDebugActive(env)) {
      return; // debug active: keep Spring Boot's correlation pattern so the traceId is logged
    }
    env.getPropertySources()
        .addFirst(
            new MapPropertySource(
                "openaev-debug-log-correlation", Map.of("logging.pattern.correlation", "")));
  }

  @Override
  public int getOrder() {
    // Before LoggingApplicationListener (HIGHEST_PRECEDENCE + 20) so the pattern is set in time.
    return Ordered.HIGHEST_PRECEDENCE + 11;
  }
}
