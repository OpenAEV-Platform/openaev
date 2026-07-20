package io.openaev.service;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.model.inject.form.Expectation;
import io.openaev.utils.fixtures.ExpectationFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExpectationService")
class ExpectationServiceTest {

  @Mock private ExpectationBuilderService expectationBuilderService;

  @InjectMocks private ExpectationService expectationService;

  @Nested
  @DisplayName("buildAvailableExpectationsForTechnicalInject()")
  class BuildAvailableExpectationsForTechnicalInject {

    @Test
    @DisplayName(
        "should return exactly [DETECTION, PREVENTION, VULNERABILITY] regardless of predefined")
    void given_technicalInject_should_returnAllThreeTechnicalExpectationsInOrder() {
      // -- GIVEN --
      Expectation detection = ExpectationFixture.createExpectation(DETECTION);
      Expectation prevention = ExpectationFixture.createExpectation(PREVENTION);
      Expectation vulnerability = ExpectationFixture.createExpectation(VULNERABILITY);

      given(expectationBuilderService.buildDetectionExpectation()).willReturn(detection);
      given(expectationBuilderService.buildPreventionExpectation()).willReturn(prevention);
      given(expectationBuilderService.buildVulnerabilityExpectation()).willReturn(vulnerability);

      // -- EXECUTE --
      List<Expectation> result = expectationService.buildAvailableExpectationsForTechnicalInject();

      // -- ASSERT --
      assertThat(result).hasSize(3).containsExactly(detection, prevention, vulnerability);
    }

    @Test
    @DisplayName("should always return 3 elements (no deduplication logic needed)")
    void given_technicalInject_should_alwaysReturnExactlyThreeElements() {
      // -- GIVEN --
      given(expectationBuilderService.buildDetectionExpectation())
          .willReturn(ExpectationFixture.createExpectation(DETECTION));
      given(expectationBuilderService.buildPreventionExpectation())
          .willReturn(ExpectationFixture.createExpectation(PREVENTION));
      given(expectationBuilderService.buildVulnerabilityExpectation())
          .willReturn(ExpectationFixture.createExpectation(VULNERABILITY));

      // -- EXECUTE --
      List<Expectation> result = expectationService.buildAvailableExpectationsForTechnicalInject();

      // -- ASSERT --
      assertThat(result).hasSize(3);
      assertThat(result.stream().map(Expectation::getType))
          .containsExactlyInAnyOrder(DETECTION, PREVENTION, VULNERABILITY);
    }
  }
}
