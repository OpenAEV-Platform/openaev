package io.openaev.api.graph.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The aggregated estate overview: a few dozen bubbles whatever the estate size, counts and not
 * nodes, so it reads the same at ten assets and at a hundred thousand.
 *
 * @param assetCount every asset of the scope
 * @param truncated true when more groups exist than the overview draws
 */
public record GraphOverviewOutput(
    @JsonProperty("overview_groups") @NotNull List<GraphOverviewGroupOutput> groups,
    @JsonProperty("overview_links") @NotNull List<GraphOverviewLinkOutput> links,
    @JsonProperty("overview_asset_count") long assetCount,
    @JsonProperty("overview_truncated") boolean truncated) {}
