package io.openaev.api.attack_path;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AttackPathOutput(
    @JsonProperty("attack_path_nodes") List<AttackPathNodeOutput> nodes,
    @JsonProperty("attack_path_edges") List<AttackPathEdgeOutput> edges,
    @JsonProperty("attack_path_stats") AttackPathStatsOutput stats) {}
