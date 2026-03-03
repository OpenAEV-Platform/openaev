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

/** Input DTO for configuring the timeout on a chaining scenario. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Input for configuring the timeout on a chaining scenario.")
public class ChainingTimeOutInput {

  /** Whether the timeout feature is enabled. */
  @Schema(
      description = "Indicates whether the timeout feature is enabled for this chaining scenario.")
  @JsonProperty("chaining_enable_time_out")
  private boolean isTimeOut;

  /** Number of hours for the timeout. Between 0 and 23. */
  @Schema(
      description = "Number of hours for the timeout of the attack chaining scenario.",
      minimum = "0",
      maximum = "23")
  @JsonProperty("chaining_time_out_hours")
  @Min(value = 0, message = "Timeout hours must be zero or greater")
  @Max(value = 24, message = "Timeout hours must be at most 23")
  private Integer timeOutHours;

  /** Number of minutes for the timeout. Between 0 and 59. */
  @Schema(
      description = "Number of minutes for the timeout of the attack chaining scenario.",
      minimum = "0",
      maximum = "59")
  @JsonProperty("chaining_time_out_minutes")
  @Min(value = 0, message = "Timeout minutes must be zero or greater")
  @Max(value = 59, message = "Timeout minutes must be at most 59")
  private Integer timeOutMinutes;
}
