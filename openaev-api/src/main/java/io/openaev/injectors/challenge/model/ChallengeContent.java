package io.openaev.injectors.challenge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.injectors.email.model.EmailContent;
import io.openaev.model.inject.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChallengeContent extends EmailContent {

  @JsonProperty("challenges")
  private List<String> challenges = new ArrayList<>();

  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();
}
