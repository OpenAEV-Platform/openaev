package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Request body for turning an autonomous scenario into a manual chained scenario. */
@Getter
@Setter
@Schema(description = "How to convert an autonomous scenario into a manual chained scenario")
public class AutonomousConvertToManualInput {

  @NotNull
  @JsonProperty("mode")
  @Schema(
      description =
          "DUPLICATE creates a new manual chained scenario from a copy and leaves the AI run"
              + " untouched; IN_PLACE turns this scenario manual for good (irreversible).")
  private ConvertToManualMode mode;
}
