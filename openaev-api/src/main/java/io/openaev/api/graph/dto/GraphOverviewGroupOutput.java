package io.openaev.api.graph.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * One bubble of the aggregated estate overview (#9447): an asset group with its member count and
 * its failure rate, or the bucket of the assets in no group ({@code ungrouped}).
 *
 * @param assetCount static members (a dynamic group is flagged and unfolded on demand)
 * @param scoredCount expectations with a result on the members
 * @param failedCount of those, the ones that failed
 * @param failureRate failedCount / scoredCount, 0 when nothing was scored
 */
public record GraphOverviewGroupOutput(
    @JsonProperty("group_id") @NotNull String groupId,
    @JsonProperty("group_name") @NotNull String groupName,
    @JsonProperty("group_asset_count") long assetCount,
    @JsonProperty("group_scored_count") long scoredCount,
    @JsonProperty("group_failed_count") long failedCount,
    @JsonProperty("group_failure_rate") double failureRate,
    @JsonProperty("group_dynamic") boolean dynamic,
    @JsonProperty("group_ungrouped") boolean ungrouped) {

  /** The id the overview gives the bucket of the assets in no group. */
  public static final String UNGROUPED_ID = "ungrouped";

  public static double rate(long failed, long scored) {
    return scored == 0 ? 0.0 : (double) failed / (double) scored;
  }
}
