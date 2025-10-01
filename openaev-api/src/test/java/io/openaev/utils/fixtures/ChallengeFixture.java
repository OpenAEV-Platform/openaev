package io.openaev.utils.fixtures;

import static io.openaev.database.model.ChallengeFlag.FLAG_TYPE.VALUE;

import io.openaev.database.model.Challenge;
import io.openaev.database.model.ChallengeFlag;
import java.util.List;
import java.util.UUID;

public class ChallengeFixture {

  public static Challenge createDefaultChallenge() {
    Challenge challenge = createChallengeWithDefaultName();
    ChallengeFlag challengeFlag = createDefaultChallengeFlag();
    challenge.setFlags(List.of(challengeFlag));
    challengeFlag.setChallenge(challenge);
    challenge.setScore(100.0);
    challenge.setMaxAttempts(2);
    return challenge;
  }

  public static ChallengeFlag createDefaultChallengeFlag() {
    ChallengeFlag challengeFlag = new ChallengeFlag();
    challengeFlag.setType(VALUE);
    challengeFlag.setValue("flag value");
    return challengeFlag;
  }

  private static Challenge createChallengeWithDefaultName() {
    return createChallengeWithName(null);
  }

  private static Challenge createChallengeWithName(String name) {
    String new_name = name == null ? "challenge-%s".formatted(UUID.randomUUID()) : name;
    Challenge challenge = new Challenge();
    challenge.setName(new_name);
    return challenge;
  }
}
