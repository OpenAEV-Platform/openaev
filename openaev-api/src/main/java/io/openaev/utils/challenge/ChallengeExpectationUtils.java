package io.openaev.utils.challenge;

import io.openaev.database.model.InjectExpectationResult;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import java.time.Instant;

public class ChallengeExpectationUtils {

  private static final String NOT_APPLICABLE = null;
  private static final String NO_RESULT = null;
  private static final Double NO_SCORE = null;

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

  public static InjectExpectationResult buildDefaultChallengeInjectExpectationResult() {
    return InjectExpectationResult.builder()
        .sourceId(CHALLENGE_SOURCE_TYPE)
        .sourceType(CHALLENGE_SOURCE_TYPE)
        .sourceName(CHALLENGE_SOURCE_NAME)
        .sourcePlatform(NOT_APPLICABLE)
        .result(NO_RESULT)
        .score(NO_SCORE)
        .date(String.valueOf(Instant.now()))
        .build();
  }
}
