package io.openaev.rest.team.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.team.query_model.TeamQueryModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TeamBasicOutput {

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

  @JsonProperty("team_contextual")
  @Schema(
      description =
          "True if the team is contextual (exists only in the scenario/simulation it is linked to)")
  private Boolean contextual;

  @JsonProperty("team_updated_at")
  @NotNull
  @Schema(description = "Update date of the team")
  private Instant updatedAt;

  public static TeamBasicOutput fromQueryModel(@NotNull final TeamQueryModel t) {
    return TeamBasicOutput.builder()
        .id(t.getId())
        .name(t.getName())
        .description(t.getDescription())
        .contextual(t.getContextual())
        .updatedAt(t.getUpdatedAt())
        .build();
  }
}
