package io.openaev.rest.scenario.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScenarioTeamUserOutput {

  @JsonProperty("scenario_id")
  @Schema(description = "ID of the scenario")
  private String scenarioId;

  @JsonProperty("team_id")
  @Schema(description = "ID of the team")
  private String teamId;

  @JsonProperty("user_id")
  @Schema(description = "ID of the user")
  private String userId;
}
