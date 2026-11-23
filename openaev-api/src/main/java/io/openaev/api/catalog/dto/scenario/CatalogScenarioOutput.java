package io.openaev.api.catalog.dto.scenario;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** The latest offered version of a catalogue scenario, and the scenario installed from it. */
public record CatalogScenarioOutput(
    @JsonProperty("catalog_scenario_external_id") @NotBlank String externalId,
    @JsonProperty("catalog_scenario_version") @NotBlank String version,
    @JsonProperty("catalog_scenario_name") String name,
    @JsonProperty("catalog_scenario_description") String description,
    @JsonProperty("catalog_scenario_author") String author,
    @JsonProperty("catalog_scenario_type") @NotNull String scenarioType,
    @JsonProperty("catalog_scenario_category") String category,
    @JsonProperty("catalog_scenario_platforms") @NotNull String[] platforms,
    @JsonProperty("catalog_scenario_published_at") Instant publishedAt,
    @JsonProperty("catalog_scenario_missing_requirements") @NotNull
        List<String> missingRequirements,
    @JsonProperty("catalog_scenario_installed_scenario_id") String installedScenarioId) {}
