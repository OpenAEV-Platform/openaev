package io.openaev.database.model.attackpath.projection;

/**
 * A global finding-type count for the collapsed graph mode (issue 6647): one row per finding type,
 * the distinct-value count that feeds the top-bar counters. Produced by a {@code GROUP BY type}, so
 * the per-finding rows are never materialized.
 */
public record AttackPathTypeCountRow(String type, long distinctValues) {}
