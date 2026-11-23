package io.openaev.api.threat_arsenal.customization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.payload.CustomizableBlock;
import io.openaev.service.payload.PayloadCustomizationMerge.Choice;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record PayloadCustomizationUpdateInput(
    @Schema(description = "The Filigran version reviewed")
        @JsonProperty("update_payload_id")
        @NotBlank
        String payloadId,
    @Schema(description = "Keep mine or take Filigran's, for every conflicting block")
        @JsonProperty("update_choices")
        Map<CustomizableBlock, Choice> choices) {}
