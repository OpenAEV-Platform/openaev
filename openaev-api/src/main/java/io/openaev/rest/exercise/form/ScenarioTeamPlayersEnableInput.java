package io.openaev.rest.exercise.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ScenarioTeamPlayersEnableInput {

  @JsonProperty("scenario_team_players")
  private List<String> playersIds = new ArrayList<>();
}
