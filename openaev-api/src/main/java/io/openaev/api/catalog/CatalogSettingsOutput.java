package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Catalogue settings of the tenant; payload auto-install is off by default (F3). */
public record CatalogSettingsOutput(
    @JsonProperty("payload_auto_install") boolean payloadAutoInstall) {}
