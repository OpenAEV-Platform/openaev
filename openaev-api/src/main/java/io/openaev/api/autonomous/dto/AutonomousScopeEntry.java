package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One entry of an autonomous run's resolved scope, as read back from the run's workflow. Carries
 * both the workflow rule {@code source} (ASSET / ASSET_GROUP / TEAM / PLAYER / MANUAL / CSV) and the
 * orchestrator target-kind {@code type} (ASSETS / ASSETS_GROUPS / TEAMS / PLAYERS / MANUAL), plus the
 * value ({@code id} - an entity id, or a raw IP / CIDR / hostname for manual rules) and a resolved
 * display {@code name} for entities.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One resolved scope entry of an autonomous run")
public class AutonomousScopeEntry {

  @JsonProperty("source")
  @Schema(description = "Workflow scope-rule source: ASSET, ASSET_GROUP, TEAM, PLAYER, MANUAL, CSV")
  private String source;

  @JsonProperty("type")
  @Schema(
      description =
          "Orchestrator target kind: ASSETS, ASSETS_GROUPS, TEAMS, PLAYERS (or MANUAL for a raw"
              + " IP / CIDR / hostname). Use this kind's ids with the authoring / scope tools.")
  private String type;

  @JsonProperty("id")
  @Schema(description = "The rule value: an entity id, or a raw IP / CIDR / hostname for MANUAL/CSV")
  private String id;

  @JsonProperty("name")
  @Schema(description = "Resolved display name for entities; falls back to the id / raw value")
  private String name;
}
