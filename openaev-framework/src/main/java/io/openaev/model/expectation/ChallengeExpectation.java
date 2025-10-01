package io.openaev.model.expectation;

import io.openaev.database.model.Challenge;
import io.openaev.database.model.InjectExpectation;
import io.openaev.model.Expectation;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChallengeExpectation implements Expectation {

  private Double score;
  private Challenge challenge;
  private boolean expectationGroup;
  private String name;
  private Long expirationTime;

  public ChallengeExpectation(
      io.openaev.model.inject.form.Expectation expectation, Challenge challenge) {
    setScore(Objects.requireNonNullElse(expectation.getScore(), 100.0));
    setChallenge(challenge);
    setName(challenge.getName());
    setExpectationGroup(expectation.isExpectationGroup());
    setExpirationTime(expectation.getExpirationTime());
  }

  @Override
  public InjectExpectation.EXPECTATION_TYPE type() {
    return InjectExpectation.EXPECTATION_TYPE.CHALLENGE;
  }
}
