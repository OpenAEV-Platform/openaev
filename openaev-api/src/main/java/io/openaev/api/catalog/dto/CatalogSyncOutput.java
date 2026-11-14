package io.openaev.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CatalogSyncOutput(
    @JsonProperty("release") String release,
    @JsonProperty("release_approver") String releaseApprover,
    @JsonProperty("applied") boolean applied,
    @JsonProperty("added") int added,
    @JsonProperty("unchanged") int unchanged,
    @JsonProperty("refused") List<Refusal> refused) {

  /** The tenant already has this release: nothing changes. */
  public static CatalogSyncOutput alreadyApplied(String release, String releaseApprover) {
    return new CatalogSyncOutput(release, releaseApprover, false, 0, 0, List.of());
  }

  public record Refusal(
      @JsonProperty("external_id") String externalId,
      @JsonProperty("version") String version,
      @JsonProperty("reason") @NotBlank String reason) {}
}
