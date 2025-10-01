package io.openaev.rest.team.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TeamBaseInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("team_name")
  @Schema(description = "Name of the team")
  private String name;

  @JsonProperty("team_description")
  @Schema(description = "Description of the team")
  private String description;

  @JsonProperty("team_organization")
  @Schema(description = "ID of the organization of the team")
  private String organizationId;

  @JsonProperty("team_tags")
  @Schema(description = "IDs of the tags of the team")
  private List<String> tagIds = new ArrayList<>();
}
