package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * External IDs of the catalogue scenarios an install-all added, and of those left to install one by
 * one.
 */
public record CatalogInstallAllOutput(
    @JsonProperty("installed") @NotNull List<String> installed,
    @JsonProperty("skipped") @NotNull List<String> skipped) {}
