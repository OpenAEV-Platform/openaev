package io.openaev.api.catalog.dto.scenario;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.catalog.scenario.MissingRequirement.Status;
import jakarta.validation.constraints.NotBlank;

/**
 * Something a catalogue scenario needs and this platform lacks, with the integration to enable and
 * whether it is deployed.
 */
public record CatalogScenarioRequirementOutput(
    @JsonProperty("requirement_label") @NotBlank String label,
    @JsonProperty("requirement_integration_id") String integrationId,
    @JsonProperty("requirement_status") Status status) {}
