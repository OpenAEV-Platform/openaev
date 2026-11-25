package io.openaev.api.threat_arsenal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** A field that differs between two versions of a payload, as people read it. */
public record PayloadFieldChangeOutput(
    @Schema(description = "Field of the payload content, e.g. payload_name or command_content")
        @JsonProperty("field")
        @NotBlank
        String field,
    @Schema(description = "Value before; null when the field was empty") @JsonProperty("before")
        JsonNode before,
    @Schema(description = "Value after; null when the field is now empty") @JsonProperty("after")
        JsonNode after) {}
