package io.openaev.api.graph.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** A directed edge of the exploration graph; {@code result} is set on TARGETS edges only. */
public record GraphEdgeOutput(
    @JsonProperty("edge_id") @NotNull String edgeId,
    @JsonProperty("edge_type") @NotNull GraphEdgeType edgeType,
    @JsonProperty("edge_source") @NotNull String source,
    @JsonProperty("edge_target") @NotNull String target,
    @JsonProperty("edge_result") GraphResult result) {

  public static String idOf(GraphEdgeType type, String source, String target) {
    return type.name() + "|" + source + "|" + target;
  }
}
