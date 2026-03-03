package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Input DTO for configuring rate limiting on a chaining scenario. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Input for configuring rate limiting on a chaining scenario.")
public class ChainingRateLimitInput {

  /** Whether rate limiting is enabled. */
  @Schema(description = "Indicates whether the rate limiting feature is enabled.")
  @JsonProperty("chaining_enable_rate_limit")
  private boolean isRateLimit;

  /** Maximum number of attempts before rate limiting kicks in. Between 1 and 99. */
  @Schema(
      description =
          "Maximum number of attempts allowed before the temporal rate limit kicks in."
              + " Useful for simulating brute-force or slow, stealthy attacks.",
      minimum = "1",
      maximum = "99")
  @JsonProperty("chaining_max_attempts")
  @Min(value = 1, message = "Max attempts must be at least 1")
  @Max(value = 99, message = "Max attempts must be at most 99")
  private Integer maxAttempts;

  /** Minutes to wait between attempts. Between 1 and 59. */
  @Schema(
      description =
          "Number of minutes to wait before allowing the next attempt."
              + " Useful for simulating brute-force or slow, stealthy attacks.",
      minimum = "1",
      maximum = "59")
  @JsonProperty("chaining_max_temporal_rate_minutes")
  @Min(value = 1, message = "Temporal rate minutes must be at least 1")
  @Max(value = 59, message = "Temporal rate minutes must be at most 59")
  private Integer maxTemporalRateMinutes;
}
