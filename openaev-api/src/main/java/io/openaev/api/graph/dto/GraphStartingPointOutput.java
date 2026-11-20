package io.openaev.api.graph.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * One entry of the starting-points rail of the graph landing (#9447): clicking it recentres the
 * graph on the entity.
 *
 * @param score what ranked it: failed expectations, assets affected, failed steps; 0 for the latest
 *     simulation
 */
public record GraphStartingPointOutput(
    @JsonProperty("point_kind") @NotNull Kind kind,
    @JsonProperty("point_node_type") @NotNull GraphNodeType nodeType,
    @JsonProperty("point_entity_id") @NotNull String entityId,
    @JsonProperty("point_label") @NotNull String label,
    @JsonProperty("point_score") long score) {

  /** The sections of the rail, in display order. */
  public enum Kind {
    EXPOSED_ASSET,
    FAILING_GROUP,
    LATEST_SIMULATION,
    TOP_FINDING,
    ATTACK_PATH,
    EXPOSED_PERSON
  }
}
