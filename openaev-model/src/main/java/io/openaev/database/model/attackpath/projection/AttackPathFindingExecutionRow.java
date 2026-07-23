package io.openaev.database.model.attackpath.projection;

/**
 * One (finding, producing execution) link (issue 5048), used to attach the executions that
 * discovered each finding to the drawer rows so the front can cross-focus the map edge and the
 * feed.
 */
public record AttackPathFindingExecutionRow(String findingId, String executionId) {}
