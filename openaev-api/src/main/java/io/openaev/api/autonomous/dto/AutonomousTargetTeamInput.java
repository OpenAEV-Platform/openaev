package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Request to guarantee a targetable team wrapping one or more persons for a human-in-the-loop step
 * (phishing, smishing, credential harvesting, ...). An OpenAEV inject can only target a TEAM whose
 * players are enabled on the run's simulation, so the orchestrator hands the player ids here and
 * gets back a ready-to-target team id - it never has to stitch team creation, membership, and
 * simulation enablement together itself (the exact source of the "Email needs at least one user"
 * failure).
 */
@Getter
@Setter
@Schema(description = "Ensure a targetable team wrapping the given persons")
public class AutonomousTargetTeamInput {

  @JsonProperty("player_ids")
  @NotEmpty
  @Schema(description = "Ids of the persons (players) that must be reachable through the team")
  private List<String> playerIds;

  @JsonProperty("name")
  @Schema(description = "Optional team name; a readable default is derived when omitted")
  private String name;

  @JsonProperty("team_id")
  @Schema(
      description =
          "Optional existing team id to augment instead of creating a new one (idempotent reuse)")
  private String teamId;

  @JsonProperty("acting_agent_id")
  @Schema(
      description =
          "Optional id of the agent on whose behalf this human target is being brought in (the"
              + " orchestrator itself, or a specialist it consulted). Used to resolve the discovery"
              + " mode enforced on the recipients: EXISTING_ONLY / SCOPED require them to be inside"
              + " the run's identity allow-scope; EXPANSIVE may reach beyond it. Omitted -> SCOPED.")
  private String actingAgentId;
}
