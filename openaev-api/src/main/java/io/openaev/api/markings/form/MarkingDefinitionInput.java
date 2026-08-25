package io.openaev.api.markings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record MarkingDefinitionInput(
    @JsonProperty("marking_type")
        @NotBlank
        @Schema(description = "Classification scale, e.g. TLP or PAP")
        String type,
    @JsonProperty("marking_name")
        @NotBlank
        @Schema(description = "Name of the marking, unique within the tenant, e.g. TLP:RED")
        String name,
    @JsonProperty("marking_order")
        @NotNull
        @Positive
        @Schema(
            description =
                "Rank within the scale — higher is more restrictive. Holding a level implies"
                    + " holding every lower level of the same scale.")
        Integer order,
    @JsonProperty("marking_color")
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex colour such as #c62828")
        @Schema(description = "Display colour, as a hex code")
        String color) {}
