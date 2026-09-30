package io.openaev.utils.inject_expectation_result;

import static io.openaev.service.InjectExpectationService.COLLECTOR;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.computeScore;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.PreventionInjectExpectation;
import io.openaev.database.model.VulnerabilityInjectExpectation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExpectationResultBuilder.computeScore")
class ExpectationResultBuilderTest {

  private static final double EXPECTED_SCORE = 100.0;

  private static <T extends BaseInjectExpectation> T withExpectedScore(T expectation) {
    expectation.setExpectedScore(EXPECTED_SCORE);
    return expectation;
  }

  /** A seeded row for a collector that has not answered yet (no result, no score). */
  private static InjectExpectationResult pending(String sourceId) {
    return InjectExpectationResult.builder()
        .sourceId(sourceId)
        .sourceType(COLLECTOR)
        .sourceName(sourceId)
        .build();
  }

  private static InjectExpectationResult answered(String sourceId, double score, String result) {
    return InjectExpectationResult.builder()
        .sourceId(sourceId)
        .sourceType(COLLECTOR)
        .sourceName(sourceId)
        .score(score)
        .result(result)
        .build();
  }

  @Nested
  @DisplayName("Detection and prevention: one successful source settles the expectation")
  class DetectionAndPrevention {

    @Test
    @DisplayName("given one source detected and others pending should return the success score")
    void given_oneSourceDetectedAndOthersPending_should_returnTheSuccessScore() {
      // Arrange
      DetectionInjectExpectation expectation = withExpectedScore(new DetectionInjectExpectation());
      List<InjectExpectationResult> results =
          List.of(
              answered("crowdstrike", 100.0, "Detected"),
              pending("sentinelone"),
              pending("microsoft-defender"),
              pending("splunk-es"));

      // Act
      Double score = computeScore(results, expectation);

      // Assert
      assertThat(score).isEqualTo(100.0);
    }

    @Test
    @DisplayName("given one source prevented and others pending should return the success score")
    void given_oneSourcePreventedAndOthersPending_should_returnTheSuccessScore() {
      // Arrange
      PreventionInjectExpectation expectation =
          withExpectedScore(new PreventionInjectExpectation());
      List<InjectExpectationResult> results =
          List.of(pending("sentinelone"), answered("crowdstrike", 100.0, "Prevented"));

      // Act
      Double score = computeScore(results, expectation);

      // Assert
      assertThat(score).isEqualTo(100.0);
    }

    @Test
    @DisplayName("given one source not detected and others pending should stay pending")
    void given_oneSourceNotDetectedAndOthersPending_should_stayPending() {
      // Arrange
      DetectionInjectExpectation expectation = withExpectedScore(new DetectionInjectExpectation());
      List<InjectExpectationResult> results =
          List.of(answered("crowdstrike", 0.0, "Not Detected"), pending("sentinelone"));

      // Act
      Double score = computeScore(results, expectation);

      // Assert
      assertThat(score).isNull();
    }

    @Test
    @DisplayName("given all sources pending should stay pending")
    void given_allSourcesPending_should_stayPending() {
      // Arrange
      DetectionInjectExpectation expectation = withExpectedScore(new DetectionInjectExpectation());
      List<InjectExpectationResult> results = List.of(pending("crowdstrike"), pending("splunk-es"));

      // Act
      Double score = computeScore(results, expectation);

      // Assert
      assertThat(score).isNull();
    }

    @Test
    @DisplayName("given all sources answered without success should return the failure score")
    void given_allSourcesAnsweredWithoutSuccess_should_returnTheFailureScore() {
      // Arrange
      DetectionInjectExpectation expectation = withExpectedScore(new DetectionInjectExpectation());
      List<InjectExpectationResult> results =
          List.of(
              answered("crowdstrike", 0.0, "Not Detected"),
              answered("splunk-es", 0.0, "Not Detected"));

      // Act
      Double score = computeScore(results, expectation);

      // Assert
      assertThat(score).isEqualTo(0.0);
    }
  }

  @Nested
  @DisplayName("Vulnerability: keeps waiting for every expected source")
  class Vulnerability {

    @Test
    @DisplayName("given one source not vulnerable and another pending should stay pending")
    void given_oneSourceNotVulnerableAndAnotherPending_should_stayPending() {
      // Arrange: another source may still report the asset vulnerable
      VulnerabilityInjectExpectation expectation =
          withExpectedScore(new VulnerabilityInjectExpectation());
      List<InjectExpectationResult> results =
          List.of(answered("nuclei", 100.0, "Not vulnerable"), pending("vulnerability-manager"));

      // Act
      Double score = computeScore(results, expectation);

      // Assert
      assertThat(score).isNull();
    }
  }
}
