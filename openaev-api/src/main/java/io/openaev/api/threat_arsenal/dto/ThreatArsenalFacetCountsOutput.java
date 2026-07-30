package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Fixed-universe facet counts for the Threat Arsenal sidebar (platforms and payload statuses),
 * computed under the current filters. Complements the domain-counts and author-counts endpoints so
 * every sidebar facet can display live counts and grey out the zero-count rows.
 */
public record ThreatArsenalFacetCountsOutput(
    @Schema(description = "Number of contracts per platform under the current filters")
        @JsonProperty("platforms")
        Map<String, Long> platforms,
    @Schema(description = "Number of contracts per payload status under the current filters")
        @JsonProperty("statuses")
        Map<String, Long> statuses) {}
