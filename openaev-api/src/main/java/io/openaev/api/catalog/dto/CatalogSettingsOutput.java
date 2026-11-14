package io.openaev.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** Whether the payloads a release brings and the tenant never installed install themselves. */
public record CatalogSettingsOutput(
    @JsonProperty("payload_auto_install") @NotNull boolean payloadAutoInstall) {}
