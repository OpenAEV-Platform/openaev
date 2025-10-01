package io.openaev.rest.team.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TeamCreateInput extends TeamBaseInput {

  @JsonProperty("team_exercises")
  @Schema(description = "Id of the simulations linked to the team")
  private List<String> exerciseIds = new ArrayList<>();

  @JsonProperty("team_scenarios")
  @Schema(description = "Id of the scenarios linked to the team")
  private List<String> scenarioIds = new ArrayList<>();

  @JsonProperty("team_contextual")
  @Schema(
      description =
          "True if the team is contextual (exists only in the scenario/simulation it is linked to)")
  private Boolean contextual = false;
}
