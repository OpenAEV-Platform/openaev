package io.openaev.api.marking_definition.form;

import static io.openaev.config.AppConfig.HEX_COLOR_FORMAT;
import static io.openaev.config.AppConfig.HEX_COLOR_REGEXP;

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
        @Pattern(regexp = HEX_COLOR_REGEXP, message = HEX_COLOR_FORMAT)
        String color,
    @JsonProperty("marking_definition_order") @NotNull @Min(0) Integer order) {}
