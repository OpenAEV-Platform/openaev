package io.openaev.database.model.attackpath.projection;

/**
 * One grouped source-to-target edge for the collapsed graph mode (issue 6647): the source identity
 * (an injector name plus its contract external id, or a source asset id) and the target key, with
 * how many executions it groups. Produced by a {@code GROUP BY} on the source and target, so the
 * per-execution rows are never materialized.
 */
public record AttackPathEdgeGroupRow(
    String sourceKind,
    String sourceInjector,
    String sourceAssetId,
    // The injector's frozen contract external id (null for agent/asset sources), so the collapsed
    // injector node is per contract, matching the full graph.
    String contractExternalId,
    // Frozen source endpoint attributes, constant per source asset within a simulation, so a
    // source-only endpoint renders them in collapsed mode too rather than a bare id.
    String sourceHostname,
    String sourceIp,
    String sourcePlatform,
    String targetKey,
    long count) {}
