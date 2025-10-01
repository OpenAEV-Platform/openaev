package io.openaev.rest.challenge.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class ChallengeTryInput {

  @JsonProperty("challenge_value")
  @NotNull
  private String value;

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
