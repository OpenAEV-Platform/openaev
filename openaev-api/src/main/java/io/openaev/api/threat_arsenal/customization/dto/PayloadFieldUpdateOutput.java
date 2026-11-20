package io.openaev.api.threat_arsenal.customization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.service.payload.CustomizableField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record PayloadFieldUpdateOutput(
    @JsonProperty("field_name") @NotNull CustomizableField field,
    @Schema(description = "The value of the base version; empty when it has none")
        @JsonProperty("field_base")
        JsonNode base,
    @JsonProperty("field_filigran") JsonNode filigran,
    @JsonProperty("field_yours") JsonNode yours,
    @Schema(description = "Both changed it differently: the user chooses one")
        @JsonProperty("field_conflict")
        @NotNull
        boolean conflict) {}
