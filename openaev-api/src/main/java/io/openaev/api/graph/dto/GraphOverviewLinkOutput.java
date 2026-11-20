package io.openaev.api.graph.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * A link of the estate overview (#9447): a simulation targeted an asset group, with the number of
 * its expectations that failed on the group and the worst result.
 */
public record GraphOverviewLinkOutput(
    @JsonProperty("link_group_id") @NotNull String groupId,
    @JsonProperty("link_simulation_id") @NotNull String simulationId,
    @JsonProperty("link_simulation_name") @NotNull String simulationName,
    @JsonProperty("link_failed_count") long failedCount,
    @JsonProperty("link_result") GraphResult result) {}
