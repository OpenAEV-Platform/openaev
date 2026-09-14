package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Identifier of the scenario resulting from the conversion")
public record AutonomousConvertToManualOutput(
    @JsonProperty("scenario_id") @Schema(description = "Id of the resulting manual scenario.")
        String scenarioId) {}
