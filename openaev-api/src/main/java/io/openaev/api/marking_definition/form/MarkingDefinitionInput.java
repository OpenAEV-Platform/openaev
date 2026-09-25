package io.openaev.api.marking_definition.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MarkingDefinitionInput(
    @JsonProperty("marking_definition_type") @NotBlank String type,
    @JsonProperty("marking_definition_definition") @NotBlank String definition,
    @JsonProperty("marking_definition_color")
        @NotBlank
        @Pattern(
            regexp = "^#[0-9a-fA-F]{6}$",
            message = "Color must be a valid hex value, e.g. #4CAF50")
        String color,
    @JsonProperty("marking_definition_order") @NotNull @Min(0) Integer order) {}
