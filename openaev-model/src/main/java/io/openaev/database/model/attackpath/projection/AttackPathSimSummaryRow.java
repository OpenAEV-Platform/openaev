package io.openaev.database.model.attackpath.projection;

/**
 * One row per simulation for the front's simulation picker (issue 6647): its id, its distinct
 * endpoint count and its execution count, so an option reads "id — N endpoints". Produced by a
 * {@code GROUP BY simulation_id}, tenant-filtered by the inspector.
 */
public record AttackPathSimSummaryRow(
    String simulationId, long endpointCount, long executionCount) {}
