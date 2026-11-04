package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Catalogue settings of the tenant; both auto-installs are on by default (F3). */
public record CatalogSettingsOutput(
    @JsonProperty("payload_auto_install") boolean payloadAutoInstall,
    @JsonProperty("scenario_auto_install") boolean scenarioAutoInstall) {}
