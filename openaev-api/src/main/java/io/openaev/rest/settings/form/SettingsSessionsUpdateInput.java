package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SettingsSessionsUpdateInput {
  @NotNull
  @Min(0)
  @JsonProperty("platform_session_max_concurrent")
  @Schema(description = "Maximum number of concurrent sessions per user (0 = unlimited)")
  private Integer platformSessionMaxConcurrent;
}
