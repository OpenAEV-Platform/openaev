package io.openaev.utils.fixtures;

import static io.openaev.utils.challenge.ChallengeAttemptUtils.buildChallengeAttempt;
import static io.openaev.utils.challenge.ChallengeAttemptUtils.buildChallengeAttemptID;

import io.openaev.database.model.ChallengeAttempt;

public class ChallengeAttemptFixture {

  public static ChallengeAttempt createChallengeAttempt(
      String challengeId, String injectStatusId, String userId, int attempt) {
    ChallengeAttempt challengeAttempt =
        buildChallengeAttempt(buildChallengeAttemptID(challengeId, injectStatusId, userId));
    challengeAttempt.setAttempt(attempt);
    return challengeAttempt;
  }
}
