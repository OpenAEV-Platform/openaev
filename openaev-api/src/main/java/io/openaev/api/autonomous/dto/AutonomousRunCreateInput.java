package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Request to create an autonomous (AI-driven) attack-path run. The objective is either free text or
 * derived from an objective template key. The run is fully autonomous: the attack-path substrate is
 * auto-provisioned and the AI orchestrator builds and executes the path itself, so the operator
 * never authors a scenario. An optional name / description only labels the auto-provisioned run.
 */
@Getter
@Setter
@Schema(description = "Input to create an autonomous attack-path run")
public class AutonomousRunCreateInput {

  @JsonProperty("objective")
  @Schema(description = "Free-text objective. Optional when an objective template key is provided.")
  private String objective;

  @JsonProperty("objective_template_key")
  @Schema(description = "Key of an objective template to seed the objective from")
  private String objectiveTemplateKey;

  @JsonProperty("name")
  @Schema(
      description = "Optional label for the auto-provisioned run. Defaults to a generated name.")
  private String name;

  @JsonProperty("description")
  @Schema(description = "Optional description for the auto-provisioned run.")
  private String description;

  @JsonProperty("scenario_id")
  @Schema(
      description =
          "Advanced/optional: seed from an existing chaining scenario instead of auto-provisioning."
              + " Leave empty for a fully autonomous run.")
  private String scenarioId;

  @JsonProperty("scope_asset_group_id")
  @Schema(description = "Optional asset group defining the initial in-scope perimeter")
  private String scopeAssetGroupId;

  @JsonProperty("agent_slug")
  @Schema(description = "Optional orchestrator agent slug override")
  private String agentSlug;
}
