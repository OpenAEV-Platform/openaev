package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.threat_arsenal.ContentHistoryOutput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** Versions tab of an installed catalogue scenario (F7): every release, then the history. */
public record CatalogScenarioVersionsOutput(
    @JsonProperty("catalog_id") @NotBlank String catalogId,
    @JsonProperty("current_version") @NotBlank String currentVersion,
    @JsonProperty("versions") @NotNull List<Version> versions,
    @JsonProperty("history") @NotNull List<ContentHistoryOutput> history) {

  public record Version(
      @JsonProperty("version") @NotBlank String version,
      @JsonProperty("status") String status,
      @JsonProperty("changelog") String changelog,
      @JsonProperty("published_at") Instant publishedAt,
      @JsonProperty("scenario_id") String scenarioId,
      @JsonProperty("is_current") boolean isCurrent,
      @JsonProperty("is_latest") boolean isLatest) {}
}
