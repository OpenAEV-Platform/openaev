package io.openaev.database.model.attackpath.projection;

/**
 * One grouped source-to-target edge for the collapsed graph mode (issue 6647): the source identity
 * (an injector name or a source asset id) and the target key, with how many executions it groups.
 * Produced by a {@code GROUP BY} on the source and target, so the per-execution rows are never
 * materialized.
 */
public record AttackPathEdgeGroupRow(
    String sourceKind, String sourceInjector, String sourceAssetId, String targetKey, long count) {}
