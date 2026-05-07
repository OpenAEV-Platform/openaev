package io.openaev.api.attack_path;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AttackPathStatsOutput(
    @JsonProperty("stats_prevented") int prevented,
    @JsonProperty("stats_detected") int detected,
    @JsonProperty("stats_undetected") int undetected,
    @JsonProperty("stats_pending") int pending,
    @JsonProperty("stats_total_actions") int totalActions,
    @JsonProperty("stats_executed_actions") int executedActions) {}
