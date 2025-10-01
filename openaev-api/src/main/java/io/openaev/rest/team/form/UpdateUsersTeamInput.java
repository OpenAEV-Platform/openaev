package io.openaev.rest.team.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
public class UpdateUsersTeamInput {

  @JsonProperty("team_users")
  @Schema(description = "The list of users the team contains")
  private List<String> userIds;
}
