package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("DebugTracingEnvironmentPostProcessor")
class DebugTracingEnvironmentPostProcessorTest {

  private final DebugTracingEnvironmentPostProcessor processor =
      new DebugTracingEnvironmentPostProcessor();

  private String tracing(MockEnvironment env) {
    processor.postProcessEnvironment(env, null);
    return env.getProperty("management.tracing.enabled");
  }

  @Test
  @DisplayName("disabled debug mode leaves tracing configuration untouched")
  void disabledLeavesTracingUntouched() {
    MockEnvironment env = new MockEnvironment().withProperty("openaev.debug.enabled", "false");
    assertThat(tracing(env)).isNull();
  }

  @Test
  @DisplayName("does not override an operator's tracing setting when debug mode is off")
  void preservesOperatorTracingWhenDebugOff() {
    MockEnvironment env = new MockEnvironment().withProperty("management.tracing.enabled", "true");
    assertThat(tracing(env)).isEqualTo("true");
  }

  @Test
  @DisplayName("refused in production keeps tracing off (same gate as the barrier)")
  void refusedInProductionKeepsTracingOff() {
    MockEnvironment env = new MockEnvironment().withProperty("openaev.debug.enabled", "true");
    // no non-production profile active -> isProduction == true -> barrier refuses -> no tracing
    assertThat(tracing(env)).isEqualTo("false");
  }

  @Test
  @DisplayName("allow-in-production override turns tracing on even in production")
  void allowInProductionTurnsTracingOn() {
    MockEnvironment env =
        new MockEnvironment()
            .withProperty("openaev.debug.enabled", "true")
            .withProperty("openaev.debug.allow-in-production", "true");
    assertThat(tracing(env)).isEqualTo("true");
  }

  @Test
  @DisplayName("a non-production profile turns tracing on")
  void nonProductionProfileTurnsTracingOn() {
    MockEnvironment env = new MockEnvironment().withProperty("openaev.debug.enabled", "true");
    env.setActiveProfiles("test");
    assertThat(tracing(env)).isEqualTo("true");
  }
}
