package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Result of a threat arsenal bulk-delete operation. */
public record ThreatArsenalBulkDeleteOutput(
    @Schema(description = "Ids of the actions that were actually deleted")
        @JsonProperty("deleted_ids")
        List<String> deletedIds,
    @Schema(description = "Number of actions that were actually deleted")
        @JsonProperty("deleted_count")
        int deletedCount) {

  public static ThreatArsenalBulkDeleteOutput of(List<String> deletedIds) {
    return new ThreatArsenalBulkDeleteOutput(deletedIds, deletedIds.size());
  }
}
