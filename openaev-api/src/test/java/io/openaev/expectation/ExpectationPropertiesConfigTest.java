package io.openaev.expectation;

import static io.openaev.expectation.ExpectationPropertiesConfig.DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExpectationPropertiesConfig")
class ExpectationPropertiesConfigTest {

  @Nested
  @DisplayName("Logging expiration time")
  class LoggingExpirationTime {

    @Test
    @DisplayName("given_logging_time_configured_should_return_it")
    void given_logging_time_configured_should_return_it() {
      // Arrange
      ExpectationPropertiesConfig config = new ExpectationPropertiesConfig();
      config.setTechnicalExpirationTime(3600L);
      config.setLoggingExpirationTime(600L);

      // Act
      long expirationTime = config.getExpirationTimeByType(EXPECTATION_TYPE.LOGGING);

      // Assert
      assertThat(expirationTime).isEqualTo(600L);
    }

    @Test
    @DisplayName("given_only_technical_time_configured_should_fall_back_to_it")
    void given_only_technical_time_configured_should_fall_back_to_it() {
      // Arrange
      ExpectationPropertiesConfig config = new ExpectationPropertiesConfig();
      config.setTechnicalExpirationTime(3600L);

      // Act
      long expirationTime = config.getLoggingExpirationTime();

      // Assert
      assertThat(expirationTime).isEqualTo(3600L);
    }

    @Test
    @DisplayName("given_nothing_configured_should_fall_back_to_technical_default")
    void given_nothing_configured_should_fall_back_to_technical_default() {
      // Arrange
      ExpectationPropertiesConfig config = new ExpectationPropertiesConfig();

      // Act
      long expirationTime = config.getLoggingExpirationTime();

      // Assert
      assertThat(expirationTime).isEqualTo(DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    }
  }
}
