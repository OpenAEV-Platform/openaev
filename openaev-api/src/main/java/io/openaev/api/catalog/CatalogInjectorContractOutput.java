package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** A contract of a catalogue injector not running on the platform yet. */
public record CatalogInjectorContractOutput(
    @JsonProperty("contract_id") @NotBlank String contractId,
    @JsonProperty("contract_label") String label,
    @JsonProperty("contract_domains") List<String> domains,
    @JsonProperty("contract_platforms") List<String> platforms,
    @JsonProperty("catalog_connector_id") @NotBlank String catalogConnectorId,
    @JsonProperty("catalog_connector_slug") String catalogConnectorSlug,
    @JsonProperty("catalog_connector_title") String catalogConnectorTitle,
    @JsonProperty("catalog_connector_logo_url") String catalogConnectorLogoUrl,
    @JsonProperty("catalog_connector_manager_supported") boolean managerSupported) {}
