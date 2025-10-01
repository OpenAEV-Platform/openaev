package io.openaev.utils.challenge;

import io.openaev.rest.exercise.form.ExpectationUpdateInput;

public class ChallengeExpectationUtils {

  public static final String CHALLENGE_SOURCE_TYPE = "challenge";
  public static final String CHALLENGE_SOURCE_NAME = "Challenge validation";

  private ChallengeExpectationUtils() {}

  public static ExpectationUpdateInput buildChallengeUpdateInput(Double score) {
    ExpectationUpdateInput expectationUpdateInput = new ExpectationUpdateInput();
    expectationUpdateInput.setSourceId(CHALLENGE_SOURCE_TYPE);
    expectationUpdateInput.setSourceType(CHALLENGE_SOURCE_TYPE);
    expectationUpdateInput.setSourceName(CHALLENGE_SOURCE_NAME);
    expectationUpdateInput.setScore(score);
    return expectationUpdateInput;
  }
}
