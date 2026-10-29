package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** One thing a catalogue item needs and this platform lacks, with the integration to enable. */
public record CatalogMissingRequirement(
    @JsonProperty("requirement_kind") @NotNull Kind kind,
    @JsonProperty("requirement_key") @NotBlank String key,
    @JsonProperty("requirement_label") @NotBlank String label,
    @JsonProperty("requirement_catalog_connector_id") String catalogConnectorId,
    @JsonProperty("requirement_catalog_connector_title") String catalogConnectorTitle,
    @JsonProperty("requirement_status") Status status) {

  public enum Kind {
    OPENAEV,
    EDITION,
    INJECTOR,
    EXECUTOR,
    CHAINING;

    /**
     * Whether the install cannot go on without it. A missing injector would import its injects
     * without their contract; a missing executor or edition only stops the run, which the scenario
     * health check reports.
     */
    public boolean blocksInstall() {
      return this == OPENAEV || this == INJECTOR || this == CHAINING;
    }
  }

  public enum Status {
    NOT_DEPLOYED,
    NOT_RUNNING,
  }
}
