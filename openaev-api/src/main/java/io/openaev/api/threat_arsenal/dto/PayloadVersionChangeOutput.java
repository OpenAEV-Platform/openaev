package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PayloadVersionChangeOutput(
    @Schema(description = "The changed field, as the payload names it")
        @JsonProperty("change_field")
        @NotBlank
        String field,
    @Schema(description = "Its value in the version just below; empty when it had none")
        @JsonProperty("change_before")
        JsonNode before,
    @JsonProperty("change_after") JsonNode after) {}
