package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CatalogSettingsInput(
    @JsonProperty("payload_auto_install") boolean payloadAutoInstall) {}
