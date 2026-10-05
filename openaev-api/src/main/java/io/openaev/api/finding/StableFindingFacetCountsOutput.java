package io.openaev.api.finding;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

public record StableFindingFacetCountsOutput(
    @Schema(description = "Finding counts grouped by effective severity")
        @JsonProperty("severities")
        Map<String, Long> severities,
    @Schema(description = "Finding counts grouped by output type") @JsonProperty("types")
        Map<String, Long> types,
    @Schema(description = "Finding counts grouped by cloud provider")
        @JsonProperty("cloud_providers")
        Map<String, Long> cloudProviders,
    @Schema(description = "Finding counts grouped by triage status")
        @JsonProperty("triage_statuses")
        Map<String, Long> triageStatuses,
    @Schema(description = "Finding counts grouped by source") @JsonProperty("sources")
        List<SourceFacetOutput> sources) {

  public record SourceFacetOutput(
      @JsonProperty("source_id") String id,
      @JsonProperty("source_name") String name,
      @JsonProperty("source_type") String type,
      @JsonProperty("source_count") long count) {}
}
