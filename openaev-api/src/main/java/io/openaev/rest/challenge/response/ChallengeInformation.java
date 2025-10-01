package io.openaev.rest.challenge.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.InjectExpectation;
import lombok.Getter;

@Getter
public class ChallengeInformation {

  @JsonProperty("challenge_detail")
  private final PublicChallenge challenge;

  @JsonProperty("challenge_expectation")
  private final InjectExpectation expectation;

  @JsonProperty("challenge_attempt")
  private final int attempt;

  public ChallengeInformation(Challenge challenge, InjectExpectation expectation, int attempt) {
    this.challenge = new PublicChallenge(challenge);
    this.expectation = expectation;
    this.attempt = attempt;
  }
}
