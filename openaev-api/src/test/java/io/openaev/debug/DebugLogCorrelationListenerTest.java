package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("DebugLogCorrelationListener")
class DebugLogCorrelationListenerTest {

  private final DebugLogCorrelationListener listener = new DebugLogCorrelationListener();

  private String correlationPattern(MockEnvironment env) {
    listener.suppressCorrelationWhenDebugOff(env);
    return env.getProperty("logging.pattern.correlation");
  }

  @Test
  @DisplayName("debug off: forces an empty correlation pattern (no empty [ ] slot in logs)")
  void disabledForcesEmptyPattern() {
    MockEnvironment env = new MockEnvironment().withProperty("openaev.debug.enabled", "false");
    assertThat(correlationPattern(env)).isEmpty();
  }

  @Test
  @DisplayName(
      "refused in production: forces an empty correlation pattern (same gate as the barrier)")
  void refusedInProductionForcesEmptyPattern() {
    // no non-production profile active -> isProduction == true -> barrier refuses -> no correlation
    MockEnvironment env = new MockEnvironment().withProperty("openaev.debug.enabled", "true");
    assertThat(correlationPattern(env)).isEmpty();
  }

  @Test
  @DisplayName("debug active via override: leaves the correlation pattern to Spring Boot")
  void allowInProductionKeepsPattern() {
    MockEnvironment env =
        new MockEnvironment()
            .withProperty("openaev.debug.enabled", "true")
            .withProperty("openaev.debug.allow-in-production", "true");
    assertThat(correlationPattern(env)).isNull();
  }

  @Test
  @DisplayName(
      "debug active via non-production profile: leaves the correlation pattern to Spring Boot")
  void nonProductionProfileKeepsPattern() {
    MockEnvironment env = new MockEnvironment().withProperty("openaev.debug.enabled", "true");
    env.setActiveProfiles("test");
    assertThat(correlationPattern(env)).isNull();
  }

  @Test
  @DisplayName(
      "runs before Spring Boot's LoggingApplicationListener (order < HIGHEST_PRECEDENCE+20)")
  void runsBeforeLoggingSystemInit() {
    assertThat(listener.getOrder())
        .isLessThan(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 20);
  }
}
