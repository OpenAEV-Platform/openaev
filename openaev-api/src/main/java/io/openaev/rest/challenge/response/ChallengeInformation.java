package io.openaev.rest.challenge.response;

import static io.openaev.api.expectations.mapper.InjectExpectationMapper.toOutput;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.expectations.dto.InjectExpectationOutput;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.InjectExpectation;
import lombok.Getter;

@Getter
public class ChallengeInformation {

  @JsonProperty("challenge_detail")
  private final PublicChallenge challenge;

  @JsonProperty("challenge_expectation")
  private final InjectExpectationOutput expectation;

  @JsonProperty("challenge_attempt")
  private final int attempt;

  public ChallengeInformation(Challenge challenge, InjectExpectation expectation, int attempt) {
    this.challenge = new PublicChallenge(challenge);
    this.expectation = expectation != null ? toOutput(expectation) : null;
    this.attempt = attempt;
  }
}
