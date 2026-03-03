package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChainingConfigurationOutput {

  @Valid
  @Schema(
      description =
          "Controls how often an attack step is executed. Useful for simulating brute-force or slow, stealthy attacks.")
  @JsonProperty("chaining_configuration_rate_limit")
  private ChainingRateLimitOutput rateLimit;

  @Valid
  @Schema(
      description =
          "Maximum total runtime for the entire attack chaining scenario. Execution stops automatically once the timeout is reached.")
  @JsonProperty("chaining_configuration_time_out")
  private ChainingTimeOutOutput timeOut;

  @Schema(
      description =
          "If safe mode is enabled, exploits that could crash the customer environment will not be executed.",
      defaultValue = "true")
  @JsonProperty("chaining_configuration_enable_safe_mode")
  private boolean isSafeMode;
}
