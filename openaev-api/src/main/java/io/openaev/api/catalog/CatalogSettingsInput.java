package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CatalogSettingsInput(
    @JsonProperty("payload_auto_install") Boolean payloadAutoInstall,
    @JsonProperty("scenario_auto_install") Boolean scenarioAutoInstall) {}
