package io.openaev.rest.scenario.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScenarioTeamUserOutput {

  @JsonProperty("scenario_id")
  private String scenarioId;

  @JsonProperty("team_id")
  private String teamId;

  @JsonProperty("user_id")
  private String userId;
}
