package io.openaev.utils.fixtures;

import static io.openaev.database.model.ChallengeFlag.FLAG_TYPE.VALUE;

import io.openaev.database.model.Challenge;
import io.openaev.database.model.ChallengeFlag;
import io.openaev.database.model.Tenant;
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

  public static Challenge createChallengeWithMaxAttempts(int maxAttempts) {
    Challenge challenge = createDefaultChallenge();
    challenge.setMaxAttempts(maxAttempts);
    return challenge;
  }

  public static ChallengeFlag createDefaultChallengeFlag() {
    ChallengeFlag challengeFlag = new ChallengeFlag();
    challengeFlag.setType(VALUE);
    challengeFlag.setValue("flag value");
    return challengeFlag;
  }

  private static Challenge createChallengeWithDefaultName() {
    Challenge challenge = new Challenge();
    challenge.setName("challenge-%s".formatted(UUID.randomUUID()));
    challenge.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    return challenge;
  }
}
