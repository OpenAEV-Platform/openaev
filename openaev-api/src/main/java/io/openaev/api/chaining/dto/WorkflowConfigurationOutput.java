package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Output DTO for a workflow configuration. */
@Getter
@Builder
@Schema(description = "Output for a workflow configuration.")
public class WorkflowConfigurationOutput {

  // -- Rate limit --

  @Schema(description = "Whether rate limiting is enabled.")
  @JsonProperty("workflow_configuration_rate_limit_enabled")
  private boolean rateLimitEnabled;

  @Schema(
      description = "Maximum number of attempts allowed before the temporal rate limit kicks in.")
  @JsonProperty("workflow_configuration_max_attempts")
  private Integer maxAttempts;

  @Schema(description = "Seconds to wait between attempts.")
  @JsonProperty("workflow_configuration_max_temporal_rate_seconds")
  private Long maxTemporalRateSeconds;

  // -- Timeout --

  @Schema(description = "Whether the timeout feature is enabled.")
  @JsonProperty("workflow_configuration_timeout_enabled")
  private boolean timeoutEnabled;

  @Schema(description = "Total timeout in seconds for the attack workflow.")
  @JsonProperty("workflow_configuration_timeout_seconds")
  private Long timeoutSeconds;

  // -- Safe mode --

  @Schema(
      description =
          "If enabled, exploits that could crash the customer environment will not be executed.")
  @JsonProperty("workflow_configuration_safe_mode_enabled")
  private boolean safeModeEnabled;

  // -- Scope rules --

  @Valid
  @Schema(description = "List scope rules")
  @JsonProperty("workflow_scope_rules")
  private List<WorkflowScopeRuleOutput> workflowScopeRules;

  // -- Scope variables --

  @Schema(description = "Custom variables available for template substitution in this workflow.")
  @JsonProperty("workflow_scope_variables")
  private List<ScopeVariableOutput> workflowScopeVariables;
}
