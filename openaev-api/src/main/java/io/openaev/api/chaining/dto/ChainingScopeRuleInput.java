package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Input for a scope rule used in chaining configuration.")
public class ChainingScopeRuleInput {

  @NotNull
  @Schema(description = "Selected list mode where the rule should be applied")
  @JsonProperty("scope_rule_selected_mode")
  private ScopeRuleSelectedMode selectedMode;

  @NotNull
  @Schema(description = "Source of the selected rule")
  @JsonProperty("scope_rule_source")
  private ScopeRuleSource ruleSource;

  @NotBlank
  @Schema(description = "Selected rule value")
  @JsonProperty("scope_rule_value")
  private String ruleValue;
}
