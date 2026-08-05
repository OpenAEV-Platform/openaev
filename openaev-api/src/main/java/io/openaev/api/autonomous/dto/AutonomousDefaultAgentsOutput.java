package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The tenant's default additional-agent configuration for autonomous runs: the agent ids consulted
 * by default and each agent's default discovery mode.
 */
@Getter
@AllArgsConstructor
@Schema(description = "Tenant default additional agents + per-agent discovery modes")
public class AutonomousDefaultAgentsOutput {

  @JsonProperty("agent_ids")
  @Schema(description = "XTM One agent ids consulted by default.")
  private List<String> agentIds;

  @JsonProperty("agent_modes")
  @Schema(description = "Default discovery mode per agent id (EXISTING_ONLY / SCOPED / EXPANSIVE).")
  private Map<String, String> agentModes;
}
