package io.openaev.expectation;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExpectationType")
class ExpectationTypeTest {

  @Nested
  @DisplayName("label")
  class Label {

    @Test
    @DisplayName("given_logging_reached_score_should_return_logged")
    void given_logging_reached_score_should_return_logged() {
      // Act
      String label = ExpectationType.label(EXPECTATION_TYPE.LOGGING, 100.0, 100.0);

      // Assert
      assertThat(label).isEqualTo("Logged");
    }

    @Test
    @DisplayName("given_logging_missed_score_should_return_not_logged")
    void given_logging_missed_score_should_return_not_logged() {
      // Act
      String label = ExpectationType.label(EXPECTATION_TYPE.LOGGING, 100.0, 0.0);

      // Assert
      assertThat(label).isEqualTo("Not Logged");
    }
  }

  @Nested
  @DisplayName("of")
  class Of {

    @Test
    @DisplayName("given_logging_value_should_return_logging")
    void given_logging_value_should_return_logging() {
      // Act
      ExpectationType type = ExpectationType.of(EXPECTATION_TYPE.LOGGING_VALUE);

      // Assert
      assertThat(type).isEqualTo(ExpectationType.LOGGING);
    }
  }
}
