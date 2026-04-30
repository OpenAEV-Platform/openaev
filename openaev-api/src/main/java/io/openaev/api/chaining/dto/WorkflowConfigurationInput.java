package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.*;

/** Input DTO for creating or updating a workflow configuration on a scenario. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Input for creating or updating a workflow configuration.")
public class WorkflowConfigurationInput {

  // -- Rate limit --

  @Schema(description = "Whether rate limiting is enabled.")
  @JsonProperty("workflow_configuration_rate_limit_enabled")
  private boolean rateLimitEnabled;

  @Schema(
      description =
          "Maximum number of attempts allowed before the temporal rate limit kicks in (1–99).")
  @JsonProperty("workflow_configuration_max_attempts")
  @Min(value = 1, message = "Max attempts must be at least 1")
  @Max(value = 99, message = "Max attempts must be at most 99")
  private Integer maxAttempts;

  @Schema(description = "Seconds to wait between attempts (1–3540).")
  @JsonProperty("workflow_configuration_max_temporal_rate_seconds")
  @Min(value = 1, message = "Temporal rate must be at least 1")
  @Max(value = 5940, message = "Temporal rate must be at most 5940 seconds (= 99 minutes)")
  private Long maxTemporalRateSeconds;

  // -- Timeout --

  @Schema(description = "Whether the timeout feature is enabled.")
  @JsonProperty("workflow_configuration_timeout_enabled")
  private boolean timeoutEnabled;

  @Schema(description = "Total timeout in seconds for the attack workflow scenario (60–86400).")
  @JsonProperty("workflow_configuration_timeout_seconds")
  @Min(value = 60, message = "Timeout seconds must be at least 60 (1 min)")
  @Max(value = 86400, message = "Timeout seconds must be at most 86400 (24 h)")
  private Long timeoutSeconds;

  // -- Safe mode --

  @Schema(
      description =
          "If enabled, exploits that could crash the customer environment will not be executed.",
      defaultValue = "true")
  @JsonProperty("workflow_configuration_safe_mode_enabled")
  private boolean safeModeEnabled;

  // -- Scope rules --

  @Valid
  @Schema(description = "List scope rules.")
  @JsonProperty("workflow_scope_rules")
  private List<WorkflowScopeRuleInput> workflowScopeRules;

  // -- Scope variables --

  @Valid
  @Schema(
      description =
          "List of custom variables available for template substitution in this workflow.")
  @JsonProperty("workflow_scope_variables")
  private List<ScopeVariableInput> workflowScopeVariables;
}
