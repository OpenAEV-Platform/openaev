package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SettingsInjectExecutionUpdateInput {
  @NotNull
  @Min(1)
  @Max(1440)
  @JsonProperty("inject_execution_threshold_minutes")
  @Schema(
      description =
          "Minutes OpenAEV waits for an inject to report a terminal trace before it is finalized"
              + " with a timeout (1 to 1440)")
  private Integer injectExecutionThresholdMinutes;
}
