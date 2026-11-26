package io.openaev.api.catalog.dto.scenario;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/** The details of one offered version of a catalogue scenario. */
public record CatalogScenarioVersionOutput(
    @JsonProperty("catalog_scenario_version") @NotBlank String version,
    @JsonProperty("catalog_scenario_published_at") Instant publishedAt,
    @JsonProperty("catalog_scenario_changelog") String changelog,
    @JsonProperty("catalog_scenario_main_focus") String mainFocus,
    @JsonProperty("catalog_scenario_severity") String severity,
    @JsonProperty("catalog_scenario_injects_count") Integer injectsCount) {}
