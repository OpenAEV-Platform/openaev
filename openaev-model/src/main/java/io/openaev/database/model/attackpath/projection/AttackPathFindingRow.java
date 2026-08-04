package io.openaev.database.model.attackpath.projection;

/**
 * Flat projection for Read B of the attack-path rebuild: one {@code attackpath_finding} joined to
 * one producing {@code executionId} (via {@code attackpath_execution_finding}). The same finding
 * appears once per producing execution, which is what keeps the finding→action trace exact.
 */
public record AttackPathFindingRow(
    String id,
    String type,
    String value,
    String endpointId,
    String endpointRaw,
    String endpointKey,
    String executionId,
    boolean isFinding) {}
