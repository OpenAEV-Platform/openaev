package io.openaev.api.catalog.dto.scenario;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** Something a catalogue scenario needs and this platform lacks, with the integration to enable. */
public record CatalogScenarioRequirementOutput(
    @JsonProperty("requirement_label") @NotBlank String label,
    @JsonProperty("requirement_integration_id") String integrationId) {}
