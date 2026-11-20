package io.openaev.api.graph.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * A slice of the exploration graph: what one request loaded, never the whole tenant.
 *
 * @param focus the node the slice is centred on, null for the landing view
 * @param truncated true when the cap cut neighbours: the UI offers "load more"
 * @param path the node ids of the path, in order, for a path request; empty otherwise
 */
public record GraphOutput(
    @JsonProperty("graph_focus") String focus,
    @JsonProperty("graph_nodes") @NotNull List<GraphNodeOutput> nodes,
    @JsonProperty("graph_edges") @NotNull List<GraphEdgeOutput> edges,
    @JsonProperty("graph_truncated") boolean truncated,
    @JsonProperty("graph_path") @NotNull List<String> path) {}
