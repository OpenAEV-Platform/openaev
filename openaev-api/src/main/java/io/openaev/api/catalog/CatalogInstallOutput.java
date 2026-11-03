package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record CatalogInstallOutput(
    @JsonProperty("scenario_id") @NotNull String scenarioId,
    @JsonProperty("scenario_version") @NotNull String version) {}
