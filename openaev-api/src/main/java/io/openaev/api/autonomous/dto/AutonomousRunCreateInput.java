package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.database.model.autonomous.AutonomousScopeTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
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

  @JsonProperty("scope_team_id")
  @Schema(
      description =
          "Optional team defining the in-scope audience for identity-targeted objectives"
              + " (phishing, human credential harvesting). Legacy single-team shortcut; prefer the"
              + " mixed 'scope' list.")
  private String scopeTeamId;

  @JsonProperty("scope")
  @Schema(
      description =
          "Optional mixed scope: a list of targetable entities (assets, asset groups, teams,"
              + " persons) the run is restricted to. Leave empty to let the AI resolve the scope.")
  private List<AutonomousScopeTarget> scope;

  @JsonProperty("scope_rules")
  @Schema(
      description =
          "Optional full scope definition seeded onto the run's scenario and simulation workflows:"
              + " allow-list and deny-list rules across every source (asset, asset group, team,"
              + " person, and manual IP / CIDR / hostname / CSV), matching the manual chained-scope"
              + " editor. Superset of 'scope' (which only carries allow-listed entities). Leave"
              + " empty to skip scoping at launch and let the AI resolve and record the scope.")
  private List<WorkflowScopeRuleInput> scopeRules;

  @JsonProperty("agent_slug")
  @Schema(description = "Optional orchestrator agent slug override")
  private String agentSlug;

  @JsonProperty("plan_mode")
  @Schema(
      description =
          "Dry-run: when true the orchestrator only designs the attack path (scope, steps,"
              + " decisions) and executes nothing. The operator can review the plan and later run"
              + " it for real. Defaults to false (immediate live run).")
  private boolean planMode = false;
}
