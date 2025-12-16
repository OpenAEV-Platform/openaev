package io.openaev.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ScenarioTeamUserDTO {

  @JsonProperty("scenario_id")
  private String scenarioId;

  @JsonProperty("team_id")
  private String teamId;

  @JsonProperty("user_id")
  private String userId;
}
