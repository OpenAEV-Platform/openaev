package io.openaev.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** The payload or scenario an install added. */
public record CatalogInstallOutput(@JsonProperty("installed_id") @NotBlank String installedId) {}
