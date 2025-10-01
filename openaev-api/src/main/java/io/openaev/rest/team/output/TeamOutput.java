package io.openaev.rest.team.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class TeamOutput {

  @JsonProperty("team_id")
  @NotBlank
  @Schema(description = "ID of the team")
  private String id;

  @JsonProperty("team_name")
  @NotBlank
  @Schema(description = "Name of the team")
  private String name;

  @JsonProperty("team_description")
  @Schema(description = "Description of the team")
  private String description;

  @Schema(description = "Simulation ids linked to this team")
  @JsonProperty("team_exercises")
  @NotBlank
  private Set<String> exercises;

  @Schema(description = "Scenario ids linked to this team")
  @JsonProperty("team_scenarios")
  @NotBlank
  private Set<String> scenarios;

  @JsonProperty("team_contextual")
  @Schema(
      description =
          "True if the team is contextual (exists only in the scenario/simulation it is linked to)")
  private Boolean contextual;

  @JsonProperty("team_tags")
  @Schema(description = "List of tags of the team")
  private Set<String> tags;

  @Schema(description = "User ids of the team")
  @JsonProperty("team_users")
  private Set<String> users;

  @JsonProperty("team_organization")
  @Schema(description = "Organization of the team")
  private String organization;

  @JsonProperty("team_updated_at")
  @NotNull
  @Schema(description = "Update date of the team")
  private Instant updatedAt;

  @JsonProperty("team_users_number")
  @Schema(description = "Number of users of the team")
  public long getUsersNumber() {
    return getUsers().size();
  }
}
