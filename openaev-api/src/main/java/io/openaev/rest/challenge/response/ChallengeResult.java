package io.openaev.rest.challenge.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ChallengeResult {
  @JsonProperty("result")
  private boolean result;

  public ChallengeResult(boolean result) {
    this.result = result;
  }
}
