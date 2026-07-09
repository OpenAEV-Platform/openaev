package io.openaev.service.expectation;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.PreventionInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.inject.service.InjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DetectionBehavior")
class DetectionBehaviorTest {

  @Mock private CollectorService collectorService;
  @Mock private InjectService injectService;
  @Mock private InjectExpectationRepository injectExpectationRepository;

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("given_detection_expectation_should_return_true")
    void given_detection_expectation_should_return_true() {
      // Arrange
      DetectionBehavior behavior =
          new DetectionBehavior(collectorService, injectService, injectExpectationRepository);
      DetectionInjectExpectation expectation = new DetectionInjectExpectation();

      // Act
      boolean supported = behavior.supports(expectation);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("given_non_detection_expectation_should_return_false")
    void given_non_detection_expectation_should_return_false() {
      // Arrange
      DetectionBehavior behavior =
          new DetectionBehavior(collectorService, injectService, injectExpectationRepository);
      PreventionInjectExpectation expectation = new PreventionInjectExpectation();

      // Act
      boolean supported = behavior.supports(expectation);

      // Assert
      assertThat(supported).isFalse();
    }
  }
}
