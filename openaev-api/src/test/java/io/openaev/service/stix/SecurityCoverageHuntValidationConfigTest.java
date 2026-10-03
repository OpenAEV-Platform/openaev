package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

@DisplayName("SecurityCoverageHuntValidationConfig")
class SecurityCoverageHuntValidationConfigTest {

  private static final String PREFIX = "openaev.security-coverage.hunt-validation";

  private static SecurityCoverageHuntValidationConfig bind(Map<String, String> properties) {
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind(PREFIX, SecurityCoverageHuntValidationConfig.class)
        .orElseGet(SecurityCoverageHuntValidationConfig::new);
  }

  @Nested
  @DisplayName("Binding")
  class Binding {

    @Test
    @DisplayName("given no property should be disabled with the documented defaults")
    void given_noProperty_should_useDocumentedDefaults() {
      // Act
      SecurityCoverageHuntValidationConfig config = bind(Map.of());

      // Assert
      assertThat(config.isEnabled()).isFalse();
      assertThat(config.getWindowPadding()).isEqualTo(Duration.ofMinutes(5));
      assertThat(config.getRequestTimeout()).isEqualTo(Duration.ofSeconds(30));
      assertThat(config.getBatchSize()).isEqualTo(50);
      assertThat(config.getMaxAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("given the documented property names should bind every value")
    void given_documentedPropertyNames_should_bindEveryValue() {
      // Act
      SecurityCoverageHuntValidationConfig config =
          bind(
              Map.of(
                  PREFIX + ".enabled", "true",
                  PREFIX + ".window-padding", "PT2M",
                  PREFIX + ".request-timeout", "PT10S",
                  PREFIX + ".batch-size", "20",
                  PREFIX + ".max-attempts", "3"));

      // Assert
      assertThat(config.isEnabled()).isTrue();
      assertThat(config.getWindowPadding()).isEqualTo(Duration.ofMinutes(2));
      assertThat(config.getRequestTimeout()).isEqualTo(Duration.ofSeconds(10));
      assertThat(config.getBatchSize()).isEqualTo(20);
      assertThat(config.getMaxAttempts()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Out-of-range values")
  class OutOfRangeValues {

    @Test
    @DisplayName("given out-of-range values should fall back to the nearest valid ones")
    void given_outOfRangeValues_should_fallBackToNearestValid() {
      // Act
      SecurityCoverageHuntValidationConfig config =
          bind(
              Map.of(
                  PREFIX + ".window-padding", "-PT1M",
                  PREFIX + ".request-timeout", "PT0S",
                  PREFIX + ".batch-size", "0",
                  PREFIX + ".max-attempts", "-2"));

      // Assert
      assertThat(config.getWindowPadding()).isEqualTo(Duration.ZERO);
      assertThat(config.getRequestTimeout())
          .isEqualTo(SecurityCoverageHuntValidationConfig.DEFAULT_REQUEST_TIMEOUT);
      assertThat(config.getBatchSize()).isEqualTo(1);
      assertThat(config.getMaxAttempts()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Production configuration")
  class ProductionConfiguration {

    @Test
    @DisplayName("given the shipped application.properties should keep the validation disabled")
    void given_shippedProperties_should_keepValidationDisabled() throws Exception {
      // Arrange
      Properties properties = new Properties();
      try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
        properties.load(in);
      }

      // Act
      SecurityCoverageHuntValidationConfig config =
          bind(
              Map.of(
                  PREFIX + ".enabled", properties.getProperty(PREFIX + ".enabled"),
                  PREFIX + ".window-padding", properties.getProperty(PREFIX + ".window-padding"),
                  PREFIX + ".request-timeout", properties.getProperty(PREFIX + ".request-timeout"),
                  PREFIX + ".batch-size", properties.getProperty(PREFIX + ".batch-size"),
                  PREFIX + ".max-attempts", properties.getProperty(PREFIX + ".max-attempts")));

      // Assert
      assertThat(config.isEnabled()).isFalse();
      assertThat(config.getWindowPadding())
          .isEqualTo(SecurityCoverageHuntValidationConfig.DEFAULT_WINDOW_PADDING);
      assertThat(config.getRequestTimeout())
          .isEqualTo(SecurityCoverageHuntValidationConfig.DEFAULT_REQUEST_TIMEOUT);
      assertThat(config.getBatchSize())
          .isEqualTo(SecurityCoverageHuntValidationConfig.DEFAULT_BATCH_SIZE);
      assertThat(config.getMaxAttempts())
          .isEqualTo(SecurityCoverageHuntValidationConfig.DEFAULT_MAX_ATTEMPTS);
    }
  }
}
