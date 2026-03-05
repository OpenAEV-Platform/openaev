package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChainingTimeOutOutput {

  @Schema(
      description = "Indicates whether the timeout feature is enabled for this chaining scenario.")
  @JsonProperty("chaining_enable_time_out")
  private boolean enableTimeOut;

  @Schema(
      description =
          "Maximum number of hours allowed for the entire attack chaining scenario to run. Must be zero or greater.",
      minimum = "0")
  @JsonProperty("chaining_time_out_hours")
  @Min(value = 0, message = "Timeout hours must be zero or greater")
  @Max(value = 24, message = "Timeout hours must be less than 24")
  private Integer timeOutHours;

  @Schema(
      description =
          "Maximum total runtime in minutes for the entire attack chaining scenario. Execution stops automatically once this timeout is reached. Must be zero or greater.",
      minimum = "0")
  @JsonProperty("chaining_time_out_minutes")
  @Min(value = 0, message = "Timeout minutes must be zero or greater")
  @Max(value = 59, message = "Timeout minutes must be less than or equal to 59")
  private Integer timeOutMinutes;
}
