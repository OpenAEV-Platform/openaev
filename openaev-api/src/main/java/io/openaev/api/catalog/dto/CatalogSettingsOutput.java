package io.openaev.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Whether the payloads and the scenarios a release brings and the tenant never had install
 * themselves.
 */
public record CatalogSettingsOutput(
    @JsonProperty("payload_auto_install") @NotNull boolean payloadAutoInstall,
    @JsonProperty("scenario_auto_install") @NotNull boolean scenarioAutoInstall) {}
