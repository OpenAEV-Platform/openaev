package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Output scope payload for workflow configuration.")
public class WorkflowScopeOutput {

  @Valid
  @Schema(description = "List scope rules.")
  @JsonProperty("workflow_scope_rules")
  private List<WorkflowScopeRuleOutput> workflowScopeRules;
}
