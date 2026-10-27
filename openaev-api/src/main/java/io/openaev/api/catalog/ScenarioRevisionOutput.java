package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ScenarioRevision;
import java.time.Instant;

public record ScenarioRevisionOutput(
    @JsonProperty("scenario_revision_id") String id,
    @JsonProperty("scenario_revision_number") int number,
    @JsonProperty("scenario_revision_reason") ScenarioRevision.Reason reason,
    @JsonProperty("scenario_revision_catalog_version") String catalogVersion,
    @JsonProperty("scenario_revision_author") String author,
    @JsonProperty("scenario_revision_created_at") Instant createdAt) {

  public static ScenarioRevisionOutput of(ScenarioRevision revision) {
    return new ScenarioRevisionOutput(
        revision.getId(),
        revision.getNumber(),
        revision.getReason(),
        revision.getCatalogVersion(),
        revision.getAuthor() == null ? null : revision.getAuthor().getName(),
        revision.getCreatedAt());
  }
}
