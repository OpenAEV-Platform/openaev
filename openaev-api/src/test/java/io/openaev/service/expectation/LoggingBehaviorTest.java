package io.openaev.service.expectation;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.LoggingInjectExpectation;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectExpectationResultRepository;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.inject.service.InjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoggingBehavior")
class LoggingBehaviorTest {

  @Mock private CollectorService collectorService;
  @Mock private InjectService injectService;
  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private InjectExpectationResultRepository injectExpectationResultRepository;

  private LoggingBehavior behavior;

  @BeforeEach
  void setUp() {
    behavior =
        new LoggingBehavior(
            collectorService,
            injectService,
            injectExpectationRepository,
            injectExpectationResultRepository);
  }

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("given_logging_expectation_should_return_true")
    void given_logging_expectation_should_return_true() {
      // Arrange
      LoggingInjectExpectation expectation = new LoggingInjectExpectation();

      // Act
      boolean supported = behavior.supports(expectation);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("given_non_logging_expectation_should_return_false")
    void given_non_logging_expectation_should_return_false() {
      // Arrange
      DetectionInjectExpectation expectation = new DetectionInjectExpectation();

      // Act
      boolean supported = behavior.supports(expectation);

      // Assert
      assertThat(supported).isFalse();
    }
  }

  @Nested
  @DisplayName("supportsFormExpectationType")
  class SupportsFormExpectationType {

    @Test
    @DisplayName("given_logging_type_should_return_true")
    void given_logging_type_should_return_true() {
      // Act
      boolean supported = behavior.supportsFormExpectationType(EXPECTATION_TYPE.LOGGING);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("given_detection_type_should_return_false")
    void given_detection_type_should_return_false() {
      // Act
      boolean supported = behavior.supportsFormExpectationType(EXPECTATION_TYPE.DETECTION);

      // Assert
      assertThat(supported).isFalse();
    }
  }

  @Nested
  @DisplayName("newTechnicalExpectation")
  class NewTechnicalExpectation {

    @Test
    @DisplayName("given_new_expectation_should_be_logging_typed_with_logging_labels")
    void given_new_expectation_should_be_logging_typed_with_logging_labels() {
      // Act
      TechnicalInjectExpectation expectation = behavior.newTechnicalExpectation();

      // Assert
      assertThat(expectation).isInstanceOf(LoggingInjectExpectation.class);
      assertThat(expectation.getType()).isEqualTo(EXPECTATION_TYPE.LOGGING);
      assertThat(expectation.getSuccessLabel()).isEqualTo("Logged");
      assertThat(expectation.getFailureLabel()).isEqualTo("Not Logged");
    }
  }
}
