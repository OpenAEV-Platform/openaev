package io.openaev.api.marking_definition.form;

import static io.openaev.config.AppConfig.HEX_COLOR_FORMAT;
import static io.openaev.config.AppConfig.HEX_COLOR_REGEXP;
import static io.openaev.database.model.MarkingDefinition.DEFINITION_MAX_LENGTH;
import static io.openaev.database.model.MarkingDefinition.TYPE_MAX_LENGTH;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MarkingDefinitionInput(
    @JsonProperty("marking_definition_type") @NotBlank @Size(max = TYPE_MAX_LENGTH) String type,
    @JsonProperty("marking_definition_definition") @NotBlank @Size(max = DEFINITION_MAX_LENGTH)
        String definition,
    @JsonProperty("marking_definition_color")
        @NotBlank
        @Pattern(regexp = HEX_COLOR_REGEXP, message = HEX_COLOR_FORMAT)
        String color,
    @JsonProperty("marking_definition_order") @NotNull @Min(0) Integer order) {}
