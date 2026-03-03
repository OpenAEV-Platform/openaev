package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChainingRateLimit {
  @JsonProperty("chaining_enable_rate_limit")
  private boolean isRateLimit;

  @JsonProperty("chaining_max_attempts")
  @Min(1)
  @Max(99)
  private Integer maxAttempts;

  @JsonProperty("chaining_max_temporal_rate_seconds")
  @Min(1)
  @Max(59)
  private Integer maxTemporalRateSeconds;
}
