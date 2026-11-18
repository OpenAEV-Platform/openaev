package io.openaev.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** The latest offered version of a catalogue scenario. */
public record CatalogScenarioOutput(
    @JsonProperty("catalog_scenario_external_id") @NotBlank String externalId,
    @JsonProperty("catalog_scenario_version") @NotBlank String version,
    @JsonProperty("catalog_scenario_name") String name) {}
