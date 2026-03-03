package io.openaev.api.chaining;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.chaining.dto.ChainingRateLimitInput;
import io.openaev.api.chaining.dto.ChainingTimeOutInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Input DTO for creating or updating a chaining configuration on a scenario. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Input for creating or updating a chaining configuration on a scenario.")
public class ChainingConfigurationInput {

  /** Rate limit configuration. */
  @Valid
  @Schema(
      description =
          "Controls how often an attack step is executed. Useful for simulating brute-force or slow, stealthy attacks.")
  @JsonProperty("chaining_configuration_rate_limit")
  private ChainingRateLimitInput rateLimit;

  /** Timeout configuration. */
  @Valid
  @Schema(
      description =
          "Maximum total runtime for the entire attack chaining scenario. Execution stops automatically once the timeout is reached.")
  @JsonProperty("chaining_configuration_time_out")
  private ChainingTimeOutInput timeOut;

  /** Whether safe mode is enabled. Prevents running exploits that could crash the environment. */
  @Schema(
      description =
          "If safe mode is enabled, exploits that could crash the customer environment will not be executed.",
      defaultValue = "true")
  @JsonProperty("chaining_configuration_enable_safe_mode")
  private boolean isSafeMode;
}
