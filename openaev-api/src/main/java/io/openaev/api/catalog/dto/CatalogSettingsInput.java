package io.openaev.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record CatalogSettingsInput(
    @JsonProperty("payload_auto_install") @NotNull Boolean payloadAutoInstall,
    @JsonProperty("scenario_auto_install") @NotNull Boolean scenarioAutoInstall) {}
