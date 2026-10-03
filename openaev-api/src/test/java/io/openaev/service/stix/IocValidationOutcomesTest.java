package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.IocValidationOutcome;
import io.openaev.database.model.IocValidationPair;
import io.openaev.database.model.IocValidationStatus;
import io.openaev.database.model.PreventionInjectExpectation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IOC validation outcomes")
class IocValidationOutcomesTest {

  private static final String PLATFORM_ID = "security-platform-asset";
  private static final String OTHER_PLATFORM_ID = "other-security-platform-asset";

  private static <T extends BaseInjectExpectation> T expectation(
      T expectation, Double score, InjectExpectationResult... results) {
    expectation.setExpectedScore(100.0);
    expectation.setScore(score);
    expectation.setResults(new ArrayList<>(List.of(results)));
    return expectation;
  }

  private static InjectExpectationResult result(String sourceId, Double score) {
    return InjectExpectationResult.builder().sourceId(sourceId).score(score).result("r").build();
  }

  private static IocValidationPair pair(IocValidationOutcome outcome) {
    IocValidationPair pair = new IocValidationPair();
    pair.setOutcome(outcome);
    return pair;
  }

  @Nested
  @DisplayName("Pair evaluation")
  class Evaluation {

    @Test
    @DisplayName("a prevention reported by the platform wins immediately")
    void given_prevention_should_bePrevented() {
      Optional<IocValidationOutcomes.Evaluation> evaluation =
          IocValidationOutcomes.evaluate(
              List.of(
                  expectation(new PreventionInjectExpectation(), 100.0, result(PLATFORM_ID, 100.0)),
                  expectation(new DetectionInjectExpectation(), null)),
              PLATFORM_ID,
              false);
      assertThat(evaluation)
          .map(IocValidationOutcomes.Evaluation::outcome)
          .contains(IocValidationOutcome.PREVENTED);
    }

    @Test
    @DisplayName("a detection waits while a prevention could still arrive")
    void given_detectionWithPendingPrevention_should_wait() {
      Optional<IocValidationOutcomes.Evaluation> evaluation =
          IocValidationOutcomes.evaluate(
              List.of(
                  expectation(new DetectionInjectExpectation(), 100.0, result(PLATFORM_ID, 100.0)),
                  expectation(new PreventionInjectExpectation(), null)),
              PLATFORM_ID,
              false);
      assertThat(evaluation).isEmpty();
    }

    @Test
    @DisplayName("a detection is reported once every expectation is evaluated")
    void given_detectionAndFailedPrevention_should_beDetected() {
      Optional<IocValidationOutcomes.Evaluation> evaluation =
          IocValidationOutcomes.evaluate(
              List.of(
                  expectation(new DetectionInjectExpectation(), 100.0, result(PLATFORM_ID, 100.0)),
                  expectation(new PreventionInjectExpectation(), 0.0, result(PLATFORM_ID, 0.0))),
              PLATFORM_ID,
              false);
      assertThat(evaluation)
          .map(IocValidationOutcomes.Evaluation::outcome)
          .contains(IocValidationOutcome.DETECTED);
    }

    @Test
    @DisplayName("results of another security platform never count")
    void given_resultsOfAnotherPlatform_should_beMissed() {
      Optional<IocValidationOutcomes.Evaluation> evaluation =
          IocValidationOutcomes.evaluate(
              List.of(
                  expectation(
                      new DetectionInjectExpectation(), 100.0, result(OTHER_PLATFORM_ID, 100.0)),
                  expectation(
                      new PreventionInjectExpectation(), 100.0, result(OTHER_PLATFORM_ID, 100.0))),
              PLATFORM_ID,
              false);
      assertThat(evaluation)
          .map(IocValidationOutcomes.Evaluation::outcome)
          .contains(IocValidationOutcome.MISSED);
      assertThat(evaluation.get().reason()).contains("reported nothing");
    }

    @Test
    @DisplayName("an evaluated test the platform answered negatively is missed")
    void given_negativeResults_should_beMissed() {
      Optional<IocValidationOutcomes.Evaluation> evaluation =
          IocValidationOutcomes.evaluate(
              List.of(
                  expectation(new DetectionInjectExpectation(), 0.0, result(PLATFORM_ID, 0.0)),
                  expectation(new PreventionInjectExpectation(), 0.0, result(PLATFORM_ID, 0.0))),
              PLATFORM_ID,
              false);
      assertThat(evaluation)
          .map(IocValidationOutcomes.Evaluation::outcome)
          .contains(IocValidationOutcome.MISSED);
      assertThat(evaluation.get().reason()).contains("neither a detection nor a prevention");
    }

    @Test
    @DisplayName("a simulation ending before the evaluation gives an error, not a miss")
    void given_finalizingWithPendingExpectations_should_beError() {
      Optional<IocValidationOutcomes.Evaluation> evaluation =
          IocValidationOutcomes.evaluate(
              List.of(
                  expectation(new DetectionInjectExpectation(), null),
                  expectation(new PreventionInjectExpectation(), null)),
              PLATFORM_ID,
              true);
      assertThat(evaluation)
          .map(IocValidationOutcomes.Evaluation::outcome)
          .contains(IocValidationOutcome.ERROR);
    }

    @Test
    @DisplayName("no expectation at all is an error once the simulation is over")
    void given_noExpectation_should_waitThenError() {
      assertThat(IocValidationOutcomes.evaluate(List.of(), PLATFORM_ID, false)).isEmpty();
      assertThat(IocValidationOutcomes.evaluate(List.of(), PLATFORM_ID, true))
          .map(IocValidationOutcomes.Evaluation::outcome)
          .contains(IocValidationOutcome.ERROR);
    }
  }

  @Nested
  @DisplayName("Request status")
  class FinalStatus {

    @Test
    @DisplayName("every pair evaluated completes the request")
    void given_allEvaluated_should_beCompleted() {
      assertThat(
              IocValidationOutcomes.finalStatus(
                  List.of(
                      pair(IocValidationOutcome.DETECTED),
                      pair(IocValidationOutcome.MISSED),
                      pair(IocValidationOutcome.PREVENTED))))
          .isEqualTo(IocValidationStatus.COMPLETED);
    }

    @Test
    @DisplayName("some pairs in error make the request partial")
    void given_someErrors_should_bePartial() {
      assertThat(
              IocValidationOutcomes.finalStatus(
                  List.of(pair(IocValidationOutcome.DETECTED), pair(IocValidationOutcome.ERROR))))
          .isEqualTo(IocValidationStatus.PARTIAL);
    }

    @Test
    @DisplayName("no evaluated pair fails the request")
    void given_onlyErrors_should_beFailed() {
      assertThat(IocValidationOutcomes.finalStatus(List.of(pair(IocValidationOutcome.ERROR))))
          .isEqualTo(IocValidationStatus.FAILED);
    }
  }
}
