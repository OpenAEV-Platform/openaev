package io.openaev.api.threat_arsenal.customization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.payload.CustomizableField;
import io.openaev.service.payload.PayloadCustomizationMerge.Choice;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record PayloadCustomizationUpdateInput(
    @Schema(description = "The Filigran version reviewed")
        @JsonProperty("update_payload_id")
        @NotBlank
        String payloadId,
    @Schema(description = "Filigran's or yours, for every conflicting field")
        @JsonProperty("update_choices")
        Map<CustomizableField, Choice> choices) {}
