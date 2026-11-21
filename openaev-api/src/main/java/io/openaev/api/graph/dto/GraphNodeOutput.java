package io.openaev.api.graph.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/**
 * A node of the exploration graph. Its id is {@code <type>:<entity id>}, unique across kinds.
 *
 * @param subtype the entity's own kind where it has one (asset type, finding type), else null
 * @param failedCount expectations failed on or by this node in the returned graph
 * @param findingCount findings attached to this node in the returned graph
 * @param date when the entity happened, where it has a date of its own (a simulation's start, a
 *     finding's last update), else null: the graph's time range filters on it
 * @param relationCounts the entity's relations in the data per relation type, the ones the caller
 *     may read, drawn or not: the badge of a node tells what an expansion would add; null where the
 *     slice was not counted
 */
public record GraphNodeOutput(
    @JsonProperty("node_id") @NotNull String nodeId,
    @JsonProperty("node_type") @NotNull GraphNodeType nodeType,
    @JsonProperty("node_entity_id") @NotNull String entityId,
    @JsonProperty("node_label") @NotNull String label,
    @JsonProperty("node_subtype") String subtype,
    @JsonProperty("node_result") GraphResult result,
    @JsonProperty("node_failed_count") long failedCount,
    @JsonProperty("node_finding_count") long findingCount,
    @JsonProperty("node_date") Instant date,
    @JsonProperty("node_relation_counts") Map<GraphEdgeType, Long> relationCounts) {

  /** A node not counted yet: {@link #relationCounts()} is null. */
  public GraphNodeOutput(
      String nodeId,
      GraphNodeType nodeType,
      String entityId,
      String label,
      String subtype,
      GraphResult result,
      long failedCount,
      long findingCount,
      Instant date) {
    this(nodeId, nodeType, entityId, label, subtype, result, failedCount, findingCount, date, null);
  }

  /** The same node carrying {@code counts}. */
  public GraphNodeOutput withRelationCounts(Map<GraphEdgeType, Long> counts) {
    return new GraphNodeOutput(
        nodeId,
        nodeType,
        entityId,
        label,
        subtype,
        result,
        failedCount,
        findingCount,
        date,
        counts);
  }

  public static String idOf(GraphNodeType type, String entityId) {
    return type.name() + ":" + entityId;
  }
}
