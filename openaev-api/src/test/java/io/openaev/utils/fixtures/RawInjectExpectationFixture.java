package io.openaev.utils.fixtures;

import io.openaev.database.raw.RawGlobalScoreExpectation;

public class RawInjectExpectationFixture {

  private record TestableRawGlobalScoreExpectation(
      String injectId,
      String exerciseId,
      String expectationType,
      Double expectationScore,
      Double expectationExpectedScore)
      implements RawGlobalScoreExpectation {

    @Override
    public String getInject_id() {
      return injectId;
    }

    @Override
    public String getExercise_id() {
      return exerciseId;
    }

    @Override
    public String getInject_expectation_type() {
      return expectationType;
    }

    @Override
    public Double getInject_expectation_score() {
      return expectationScore;
    }

    @Override
    public Double getInject_expectation_expected_score() {
      return expectationExpectedScore;
    }
  }

  public static RawGlobalScoreExpectation createDefaultInjectExpectation(
      String expectationType, Double expectationScore, Double expectationExpectedScore) {
    return new TestableRawGlobalScoreExpectation(
        null, null, expectationType, expectationScore, expectationExpectedScore);
  }
}
