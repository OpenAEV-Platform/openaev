package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.autonomous.AutonomousScopeTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Request for the orchestrator to SET (replace) an autonomous run's resolved scope. The provided
 * targets become the run's ALLOWLIST perimeter on both the scenario and the live simulation, so the
 * scope the AI just resolved (e.g. "DESKTOP-81B7CHJ only") is enforced and shown in the Scope tab
 * instead of living only in the orchestrator's reasoning. An empty list clears the allow-list.
 */
@Getter
@Setter
@Schema(description = "Input for the orchestrator to set an autonomous run's resolved scope")
public class AutonomousScopeUpdateInput {

  @JsonProperty("scope")
  @Schema(
      description =
          "The resolved scope: a list of targets (assets, asset groups, teams, persons) that become"
              + " the run's allow-list. Replaces any previous allow-list. Empty clears it.")
  private List<AutonomousScopeTarget> scope;
}
