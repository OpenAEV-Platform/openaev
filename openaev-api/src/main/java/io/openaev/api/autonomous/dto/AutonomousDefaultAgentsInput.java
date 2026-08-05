package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Input to set the tenant's default additional agents - the XTM One agent ids the autonomous
 * orchestrator consults by default on every new run (in addition to the built-in payload creator),
 * plus each agent's default discovery mode.
 */
@Getter
@Setter
@Schema(description = "Tenant default additional agents for autonomous runs")
public class AutonomousDefaultAgentsInput {

  @JsonProperty("agent_ids")
  @Schema(description = "XTM One agent ids to consult by default. Empty clears the default.")
  private List<String> agentIds;

  @JsonProperty("agent_modes")
  @Schema(
      description =
          "Default discovery mode per agent id (EXISTING_ONLY / SCOPED / EXPANSIVE): how much"
              + " latitude the agent has to create new assets / findings / persons from recon on"
              + " the fly. Agents omitted here default to SCOPED.")
  private Map<String, String> agentModes;
}
