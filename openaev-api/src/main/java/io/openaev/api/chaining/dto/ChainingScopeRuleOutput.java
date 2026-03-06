package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeRuleValueType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Output for a scope rule used in chaining configuration.")
public class ChainingScopeRuleOutput {

  @Schema(description = "Selected list mode where the rule is applied.")
  @JsonProperty("scope_rule_selected_mode")
  private ScopeRuleSelectedMode selectedMode;

  @Schema(description = "Source of the selected item")
  @JsonProperty("scope_rule_source")
  private ScopeRuleSource source;

  @Schema(description = "Selected item value")
  @JsonProperty("scope_rule_value")
  private String ruleValue;

  @Schema(description = "Type of selected item value")
  @JsonProperty("scope_rule_value_type")
  private ScopeRuleValueType ruleValueType;
}
