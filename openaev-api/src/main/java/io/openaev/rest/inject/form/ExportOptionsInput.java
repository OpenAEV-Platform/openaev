package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ExportOptionsInput {
  @JsonProperty("with_players")
  private boolean withPlayers = false;

  @JsonProperty("with_teams")
  private boolean withTeams = false;

  @JsonProperty("with_variable_values")
  private boolean withVariableValues = false;
}
