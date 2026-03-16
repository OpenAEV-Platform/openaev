package io.openaev.api.chaining;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

/** Input DTO for creating or updating a chaining configuration on a scenario. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Input for creating or updating a chaining configuration on a scenario.")
public class ChainingConfigurationInput {

  // -- Rate limit --

  @Schema(description = "Whether rate limiting is enabled.")
  @JsonProperty("chaining_configuration_rate_limit_enabled")
  private boolean rateLimitEnabled;

  @Schema(
      description =
          "Maximum number of attempts allowed before the temporal rate limit kicks in (1–99).")
  @JsonProperty("chaining_configuration_max_attempts")
  @Min(value = 1, message = "Max attempts must be at least 1")
  @Max(value = 99, message = "Max attempts must be at most 99")
  private Integer maxAttempts;

  @Schema(description = "Minutes to wait between attempts (1–59).")
  @JsonProperty("chaining_configuration_max_temporal_rate_seconds")
  @Min(value = 1, message = "Temporal rate must be at least 1")
  @Max(value = 59, message = "Temporal rate must be at most 59")
  private Long maxTemporalRateSeconds;

  // -- Timeout --

  @Schema(description = "Whether the timeout feature is enabled.")
  @JsonProperty("chaining_configuration_timeout_enabled")
  private boolean timeoutEnabled;

  @Schema(description = "Total timeout in seconds for the attack chaining scenario (0–86400).")
  @JsonProperty("chaining_configuration_timeout_seconds")
  @Min(value = 0, message = "Timeout seconds must be zero or greater")
  @Max(value = 86400, message = "Timeout seconds must be at most 86400 (24 h)")
  private Long timeoutSeconds;

  // -- Safe mode --

  @Schema(
      description =
          "If enabled, exploits that could crash the customer environment will not be executed.",
      defaultValue = "true")
  @JsonProperty("chaining_configuration_safe_mode_enabled")
  private boolean safeModeEnabled;
}
