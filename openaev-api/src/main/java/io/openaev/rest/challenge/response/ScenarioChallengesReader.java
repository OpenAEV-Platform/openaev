package io.openaev.rest.challenge.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Scenario;
import io.openaev.rest.scenario.response.PublicScenario;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScenarioChallengesReader {

  @JsonProperty("scenario_id")
  private String id;

  @JsonProperty("scenario_information")
  private PublicScenario scenario;

  @JsonProperty("scenario_challenges")
  private List<ChallengeInformation> scenarioChallenges = new ArrayList<>();

  public ScenarioChallengesReader(Scenario scenario) {
    this.id = scenario.getId();
    this.scenario = new PublicScenario(scenario);
  }
}
