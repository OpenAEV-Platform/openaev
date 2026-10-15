package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CatalogSyncOutput(
    @JsonProperty("release") String release,
    @JsonProperty("applied") boolean applied,
    @JsonProperty("added") int added,
    @JsonProperty("unchanged") int unchanged,
    @JsonProperty("refused") List<Refusal> refused) {

  public record Refusal(
      @JsonProperty("external_id") String externalId,
      @JsonProperty("version") String version,
      @JsonProperty("reason") @NotBlank String reason) {}
}
