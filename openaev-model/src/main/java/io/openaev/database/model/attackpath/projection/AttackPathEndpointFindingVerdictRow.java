package io.openaev.database.model.attackpath.projection;

/**
 * Flat projection for the endpoint expand read (issue 6647): a finding on one endpoint joined to
 * one of its producing executions, carrying that execution's three status columns. One row per
 * (finding, producer); the service groups per (type, value) and worst-of aggregates the verdicts.
 * The {@code AttackPathFinding} join in the query is the tenant fail-closed scope.
 */
public record AttackPathEndpointFindingVerdictRow(
    String type,
    String value,
    String preventionStatus,
    String detectionStatus,
    String vulnerabilityStatus) {}
