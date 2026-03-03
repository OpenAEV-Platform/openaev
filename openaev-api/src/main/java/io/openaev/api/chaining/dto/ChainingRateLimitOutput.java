package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChainingRateLimitOutput {

  @Schema(
      description =
          "Indicates whether the rate limiting feature is enabled for this chaining scenario.")
  @JsonProperty("chaining_enable_rate_limit")
  private boolean isRateLimit;

  @Schema(
      description =
          "Maximum number of attempts allowed before the temporal rate limit kicks in. Useful for simulating brute-force or slow, stealthy attacks.",
      defaultValue = "1",
      minimum = "0")
  @JsonProperty("chaining_max_attempts")
  @Min(value = 0, message = "Max attempts must be zero or greater")
  private Integer maxAttempts;

  @Schema(
      description =
          "Number of seconds to wait before allowing the next attempt for the execution of an attack. Useful for simulating brute-force or slow, stealthy attacks.",
      defaultValue = "3600",
      minimum = "0")
  @JsonProperty("chaining_max_temporal_rate_minutes")
  @Min(value = 0, message = "Temporal rate minutes must be zero or greater")
  private Integer maxTemporalRateMinutes;
}
