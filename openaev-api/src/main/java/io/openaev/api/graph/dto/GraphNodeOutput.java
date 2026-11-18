package io.openaev.api.graph.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * A node of the exploration graph. Its id is {@code <type>:<entity id>}, unique across kinds.
 *
 * @param subtype the entity's own kind where it has one (asset type, finding type), else null
 * @param failedCount expectations failed on or by this node in the returned graph
 * @param findingCount findings attached to this node in the returned graph
 */
public record GraphNodeOutput(
    @JsonProperty("node_id") @NotNull String nodeId,
    @JsonProperty("node_type") @NotNull GraphNodeType nodeType,
    @JsonProperty("node_entity_id") @NotNull String entityId,
    @JsonProperty("node_label") @NotNull String label,
    @JsonProperty("node_subtype") String subtype,
    @JsonProperty("node_result") GraphResult result,
    @JsonProperty("node_failed_count") long failedCount,
    @JsonProperty("node_finding_count") long findingCount) {

  public static String idOf(GraphNodeType type, String entityId) {
    return type.name() + ":" + entityId;
  }
}
