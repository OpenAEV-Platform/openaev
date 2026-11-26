package io.openaev.api.threat_arsenal.customization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PayloadCustomizationOutput(
    @Schema(description = "The action of the Filigran version the copy is built from")
        @JsonProperty("customization_base_action_id")
        @NotBlank
        String baseActionId,
    @JsonProperty("customization_base_version") @NotBlank String baseVersion,
    @Schema(description = "The newer Filigran version the copy can take; empty when none")
        @JsonProperty("customization_update")
        PayloadCustomizationUpdateOutput update) {}
