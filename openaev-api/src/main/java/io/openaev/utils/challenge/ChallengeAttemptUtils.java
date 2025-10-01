package io.openaev.utils.challenge;

import io.openaev.database.model.ChallengeAttempt;
import io.openaev.database.model.ChallengeAttemptId;
import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.NotNull;

public class ChallengeAttemptUtils {

  private ChallengeAttemptUtils() {}

  public static ChallengeAttemptId buildChallengeAttemptID(
      @NotBlank final String challengeId,
      @NotBlank final String injectStatusId,
      @NotBlank final String userId) {
    ChallengeAttemptId challengeAttemptId = new ChallengeAttemptId();
    challengeAttemptId.setChallengeId(challengeId);
    challengeAttemptId.setInjectStatusId(injectStatusId);
    challengeAttemptId.setUserId(userId);
    return challengeAttemptId;
  }

  public static ChallengeAttempt buildChallengeAttempt(
      @NotNull final ChallengeAttemptId challengeAttemptId) {
    ChallengeAttempt challengeAttempt = new ChallengeAttempt();
    challengeAttempt.setCompositeId(challengeAttemptId);
    challengeAttempt.setAttempt(0);
    return challengeAttempt;
  }
}
