package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@DisplayName("DebugProperties binding")
class DebugPropertiesBindingTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @Test
  @DisplayName("defaults are safe (disabled, masking on)")
  void defaults() {
    runner.run(
        context -> {
          DebugProperties props = context.getBean(DebugProperties.class);
          assertThat(props.isEnabled()).isFalse();
          assertThat(props.getSql().isEnabled()).isTrue();
          assertThat(props.getJfr().isEnabled()).isTrue();
          assertThat(props.getJfr().getMaxSize()).isEqualTo(DataSize.ofMegabytes(100));
          assertThat(props.getMasking().isEnabled()).isTrue();
          assertThat(props.getMasking().getMask()).isEqualTo("***MASKED***");
        });
  }

  @Test
  @DisplayName("binds nested values: DataSize, Duration and overridden lists")
  void bindsCustomValues() {
    runner
        .withPropertyValues(
            "openaev.debug.enabled=true",
            "openaev.debug.warning-interval=2m",
            "openaev.debug.jfr.max-size=250MB",
            "openaev.debug.jfr.duration=30s",
            "openaev.debug.sql.slow-query-threshold=75ms",
            "openaev.debug.masking.mask=<hidden>",
            "openaev.debug.masking.sensitive-keys=alpha,beta",
            "openaev.debug.masking.value-patterns=\\d{4}")
        .run(
            context -> {
              DebugProperties props = context.getBean(DebugProperties.class);
              assertThat(props.isEnabled()).isTrue();
              assertThat(props.getWarningInterval()).isEqualTo(Duration.ofMinutes(2));
              assertThat(props.getJfr().getMaxSize()).isEqualTo(DataSize.ofMegabytes(250));
              assertThat(props.getJfr().getDuration()).isEqualTo(Duration.ofSeconds(30));
              assertThat(props.getSql().getSlowQueryThreshold()).isEqualTo(Duration.ofMillis(75));
              assertThat(props.getMasking().getMask()).isEqualTo("<hidden>");
              assertThat(props.getMasking().getSensitiveKeys()).containsExactly("alpha", "beta");
              assertThat(props.getMasking().getValuePatterns()).containsExactly("\\d{4}");

              // The custom configuration is actually honoured by the masker.
              SensitiveDataMasker masker = new SensitiveDataMasker(props.getMasking());
              assertThat(masker.maskValue("alpha", "x")).isEqualTo("<hidden>");
              assertThat(masker.maskText("pin 1234 here"))
                  .contains("<hidden>")
                  .doesNotContain("1234");
              assertThat(masker.maskValue("password", "x")).isEqualTo("x"); // no longer sensitive
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(DebugProperties.class)
  static class Config {}
}
