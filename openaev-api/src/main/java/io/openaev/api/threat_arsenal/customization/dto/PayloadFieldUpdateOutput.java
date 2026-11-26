package io.openaev.api.threat_arsenal.customization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.service.payload.CustomizableBlock;
import io.openaev.service.payload.CustomizableField;
import io.openaev.service.payload.PayloadCustomizationMerge.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record PayloadFieldUpdateOutput(
    @JsonProperty("field_name") @NotNull CustomizableField field,
    @Schema(description = "The fields of a block conflict together: one choice settles them all")
        @JsonProperty("field_block")
        @NotNull
        CustomizableBlock block,
    @Schema(description = "The value of the base version; empty when it has none")
        @JsonProperty("field_base")
        JsonNode base,
    @JsonProperty("field_filigran") JsonNode filigran,
    @JsonProperty("field_yours") JsonNode yours,
    @Schema(description = "What the update does with the field; a conflict needs a choice")
        @JsonProperty("field_status")
        @NotNull
        Status status) {}
