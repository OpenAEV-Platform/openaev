package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.swagger.v3.oas.annotations.media.Schema;
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
}
