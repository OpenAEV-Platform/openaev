package io.openaev.database.model.attackpath.projection;

/**
 * A per-endpoint finding-type count for the collapsed graph mode (issue 6647): for one endpoint and
 * one finding type, its distinct-value count, used to summarise findings on the collapsed endpoint
 * node. Produced by a {@code GROUP BY endpoint_key, type}, so the per-finding rows are never
 * materialized.
 */
public record AttackPathEndpointTypeCountRow(
    String endpointKey, String type, long distinctValues) {}
