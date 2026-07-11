package io.openaev.database.model.attackpath.projection;

import java.time.Instant;

/**
 * One endpoint of a simulation, aggregated for the collapsed graph mode (issue 6647): the target
 * key, a representative of its frozen display attributes, and the counts used to colour it by
 * worst-case severity — {@code redCount} executions neither prevented nor detected (the worst),
 * {@code orangeCount} detected but not prevented. Produced by a {@code GROUP BY target_key}, so the
 * per-execution rows are never materialized.
 */
public record AttackPathEndpointGroupRow(
    String targetKey,
    String targetAssetId,
    String targetHostname,
    String targetIp,
    String targetPlatform,
    Instant lastExecutedAt,
    long redCount,
    long orangeCount) {}
