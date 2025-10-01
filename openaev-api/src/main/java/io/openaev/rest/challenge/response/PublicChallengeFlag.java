package io.openaev.rest.challenge.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ChallengeFlag;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PublicChallengeFlag {

  @JsonProperty("flag_id")
  private String id;

  @JsonProperty("flag_type")
  private ChallengeFlag.FLAG_TYPE type;

  @JsonProperty("flag_challenge")
  private String challenge;

  public PublicChallengeFlag(ChallengeFlag challengeFlag) {
    this.id = challengeFlag.getId();
    this.type = challengeFlag.getType();
    this.challenge = challengeFlag.getChallenge().getId();
  }
}
