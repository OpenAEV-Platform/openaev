package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Result of ensuring a targetable team: a contextual team attached to the run's simulation with the
 * given players enabled for delivery. The orchestrator targets {@code team_id} in its next chained
 * step and the email/SMS inject resolves real recipients.
 */
@Getter
@AllArgsConstructor
@Schema(description = "A team that is ready to be targeted by a human-in-the-loop inject")
public class AutonomousTargetTeamResult {

  @JsonProperty("team_id")
  @Schema(description = "Id of the contextual team - pass it as an inject target (team_ids)")
  private String teamId;

  @JsonProperty("team_name")
  @Schema(description = "Name of the team")
  private String teamName;

  @JsonProperty("player_ids")
  @Schema(description = "Ids of the players now enabled on the simulation through this team")
  private List<String> playerIds;
}
