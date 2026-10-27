package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** External IDs of the catalogue payloads an install added. */
public record CatalogPayloadInstallOutput(
    @JsonProperty("installed") @NotNull List<String> installed) {}
