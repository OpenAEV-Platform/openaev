package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record ScenarioCatalogStateOutput(
    @JsonProperty("scenario_catalog_state_revoked") @NotNull boolean revoked,
    @JsonProperty("scenario_catalog_state_newer_version") String newerVersion,
    @JsonProperty("scenario_catalog_state_last_update") ScenarioRevisionOutput lastUpdate) {}
