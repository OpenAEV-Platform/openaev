package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSnapshotStatus;
import io.openaev.database.model.ScopeRuleSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Output for a scope rule used in workflow configuration.")
public class WorkflowScopeRuleOutput {

  @Schema(description = "ID of the scope rule.")
  @JsonProperty("workflow_scope_rule_id")
  private String id;

  @Schema(description = "Selected list mode where the rule is applied.")
  @JsonProperty("workflow_scope_rule_selected_mode")
  private ScopeRuleSelectedMode selectedMode;

  @Schema(description = "Source of the selected item")
  @JsonProperty("workflow_scope_rule_source")
  private ScopeRuleSource ruleSource;

  @Schema(description = "Selected item value")
  @JsonProperty("workflow_scope_rule_value")
  private String ruleValue;

  @Schema(
      description =
          "Change status vs the frozen snapshots (launched simulation only; null for draft / "
              + "scenario, where the frontend resolves live).")
  @JsonProperty("workflow_scope_rule_status")
  private ScopeRuleSnapshotStatus status;

  @Schema(description = "Frozen label at launch (for display when the target was deleted).")
  @JsonProperty("workflow_scope_rule_snapshot_start_label")
  private String snapshotStartLabel;

  @Schema(description = "Frozen composition at launch, with agents (asset / group rules).")
  @JsonProperty("workflow_scope_rule_snapshot_start_assets")
  private List<AssetSnapshotOutput> snapshotStartAssets;

  @Schema(description = "Frozen label at end of run (null while the simulation is still running).")
  @JsonProperty("workflow_scope_rule_snapshot_end_label")
  private String snapshotEndLabel;

  @Schema(description = "Frozen composition at end of run (empty while still running).")
  @JsonProperty("workflow_scope_rule_snapshot_end_assets")
  private List<AssetSnapshotOutput> snapshotEndAssets;

  @Schema(
      description =
          "Display-name snapshot of the referenced asset / asset group, captured when the rule was"
              + " created or updated. Lets a past simulation's scope stay readable after the"
              + " referenced asset / group is deleted. Null for non-asset rules or when the id could"
              + " not be resolved within the tenant.")
  @JsonProperty("workflow_scope_rule_value_label")
  private String ruleValueLabel;
}
