package io.openaev.api.catalog.dto.scenario;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CatalogScenarioInstallOutput(
    @JsonProperty("installed_scenario_id") @NotBlank String installedScenarioId) {}
