package io.openaev.api.marking_definition.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record MarkingDefinitionOutput(
    @JsonProperty("marking_definition_id") @NotBlank String id,
    @JsonProperty("marking_definition_type") @NotBlank String type,
    @JsonProperty("marking_definition_definition") @NotBlank String definition,
    @JsonProperty("marking_definition_color") String color,
    @JsonProperty("marking_definition_order") @NotNull Integer order,
    @JsonProperty("marking_definition_protected") @NotNull Boolean protectedDefinition,
    @JsonProperty("marking_definition_created_at") @NotNull Instant createdAt) {}
