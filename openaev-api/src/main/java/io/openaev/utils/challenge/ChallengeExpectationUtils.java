package io.openaev.utils.challenge;

import io.openaev.database.model.InjectExpectationResult;
import io.openaev.service.expectation.ExpectationVerdict;
import java.time.Instant;

public class ChallengeExpectationUtils {

  private static final String NOT_APPLICABLE = null;
  private static final String NO_RESULT = null;
  private static final Double NO_SCORE = null;
  private static final String NO_ASSET = null;

  public static final String CHALLENGE_SOURCE_TYPE = "challenge";
  public static final String CHALLENGE_SOURCE_NAME = "Challenge validation";

  private ChallengeExpectationUtils() {}

  public static ExpectationVerdict buildChallengeVerdict(Double score) {
    return ExpectationVerdict.builder()
        .sourceId(CHALLENGE_SOURCE_TYPE)
        .sourceType(CHALLENGE_SOURCE_TYPE)
        .sourceName(CHALLENGE_SOURCE_NAME)
        .sourceAssetId(null)
        .score(score)
        .build();
  }

  public static InjectExpectationResult buildDefaultChallengeInjectExpectationResult() {
    return InjectExpectationResult.builder()
        .sourceId(CHALLENGE_SOURCE_TYPE)
        .sourceType(CHALLENGE_SOURCE_TYPE)
        .sourceName(CHALLENGE_SOURCE_NAME)
        .sourcePlatform(NOT_APPLICABLE)
        .result(NO_RESULT)
        .score(NO_SCORE)
        .sourceAssetId(NO_ASSET)
        .date(String.valueOf(Instant.now()))
        .build();
  }
}
