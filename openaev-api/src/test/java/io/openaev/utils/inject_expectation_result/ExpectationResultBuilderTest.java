package io.openaev.utils.inject_expectation_result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExpectationResultBuilderTest {

  @ParameterizedTest(name = "{0} settled by one source: {1}")
  @CsvSource({"LOGGING, true", "DETECTION, true", "VULNERABILITY, false"})
  @DisplayName("given one source reached the expected score should settle only the right types")
  void given_one_source_reached_expected_score_should_settle_only_the_right_types(
      BaseInjectExpectation.EXPECTATION_TYPE type, boolean settledByAnySource) {
    // Arrange
    BaseInjectExpectation expectation =
        InjectExpectationFixture.createExpectationForTypeAndScoreAndExpectedScore(
            type, null, 100.0);
    List<InjectExpectationResult> results =
        List.of(
            InjectExpectationResult.builder()
                .sourceId("siem")
                .result("Logged")
                .score(100.0)
                .build(),
            InjectExpectationResult.builder().sourceId("edr").build());

    // Act
    Double score = ExpectationResultBuilder.computeResultsScore(results, expectation);

    // Assert
    if (settledByAnySource) {
      assertEquals(100.0, score);
    } else {
      assertNull(score, "a type settled by all its sources waits for the pending one");
    }
  }
}
