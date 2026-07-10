package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

@DisplayName("DebugEnabledCondition (production barrier)")
class DebugEnabledConditionTest {

  private final DebugEnabledCondition condition = new DebugEnabledCondition();

  private boolean matches(String[] profiles, Map<String, Object> props) {
    StandardEnvironment env = new StandardEnvironment();
    env.setActiveProfiles(profiles);
    env.getPropertySources().addFirst(new MapPropertySource("test", props));
    ConditionContext context = mock(ConditionContext.class);
    when(context.getEnvironment()).thenReturn(env);
    return condition.matches(context, null);
  }

  @Test
  @DisplayName("off when not enabled, whatever the profile")
  void offWhenDisabled() {
    assertThat(matches(new String[] {"test"}, Map.of("openaev.debug.enabled", "false"))).isFalse();
    assertThat(matches(new String[] {}, Map.of())).isFalse();
  }

  @Test
  @DisplayName("on in a non-production profile without override")
  void onInNonProduction() {
    assertThat(matches(new String[] {"test"}, Map.of("openaev.debug.enabled", "true"))).isTrue();
    assertThat(matches(new String[] {"dev"}, Map.of("openaev.debug.enabled", "true"))).isTrue();
    assertThat(matches(new String[] {"ci"}, Map.of("openaev.debug.enabled", "true"))).isTrue();
  }

  @Test
  @DisplayName("refused in production without the override")
  void refusedInProduction() {
    assertThat(matches(new String[] {}, Map.of("openaev.debug.enabled", "true"))).isFalse();
    assertThat(matches(new String[] {"prod"}, Map.of("openaev.debug.enabled", "true"))).isFalse();
  }

  @Test
  @DisplayName("allowed in production with the explicit override")
  void allowedWithOverride() {
    assertThat(
            matches(
                new String[] {},
                Map.of(
                    "openaev.debug.enabled", "true",
                    "openaev.debug.allow-in-production", "true")))
        .isTrue();
  }

  @Test
  @DisplayName("isProduction is the absence of dev/test/ci")
  void isProductionDetection() {
    StandardEnvironment prod = new StandardEnvironment();
    assertThat(DebugEnabledCondition.isProduction(prod)).isTrue();

    StandardEnvironment test = new StandardEnvironment();
    test.setActiveProfiles("test");
    assertThat(DebugEnabledCondition.isProduction((Environment) test)).isFalse();
  }
}
