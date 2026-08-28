package io.openaev.api.marking_definition.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MarkingDefinitionInput(
    @JsonProperty("marking_definition_type") @NotBlank String type,
    @JsonProperty("marking_definition_definition") @NotBlank String definition,
    @JsonProperty("marking_definition_color") String color,
    @JsonProperty("marking_definition_order") @NotNull @Min(0) Integer order) {}
