package io.openaev;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.debug.DebugLogCorrelationListener;
import io.openaev.debug.DebugTracingContextInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Plain unit test of the production wiring in {@link App} (no context is started). */
@DisplayName("App production wiring")
class AppWiringTest {

  private long debugTracingInitializerCount(SpringApplicationBuilder builder) {
    return builder.build().getInitializers().stream()
        .filter(DebugTracingContextInitializer.class::isInstance)
        .count();
  }

  private long debugLogCorrelationListenerCount(SpringApplicationBuilder builder) {
    return builder.build().getListeners().stream()
        .filter(DebugLogCorrelationListener.class::isInstance)
        .count();
  }

  @Test
  @DisplayName(
      "App.application() explicitly registers the debug tracing initializer for production")
  void registersDebugTracingInitializer() {
    // The test classpath also registers the initializer via spring.factories, so assert that
    // App.application() adds exactly one more instance on top of that baseline. This fails if the
    // explicit production registration is ever removed from App.
    long baseline = debugTracingInitializerCount(new SpringApplicationBuilder(App.class));

    assertThat(debugTracingInitializerCount(App.application())).isEqualTo(baseline + 1);
  }

  @Test
  @DisplayName(
      "App.application() explicitly registers the debug log correlation listener for production")
  void registersDebugLogCorrelationListener() {
    // The listener is only wired via App (not spring.factories), so production must add exactly
    // one.
    long baseline = debugLogCorrelationListenerCount(new SpringApplicationBuilder(App.class));

    assertThat(debugLogCorrelationListenerCount(App.application())).isEqualTo(baseline + 1);
  }
}
