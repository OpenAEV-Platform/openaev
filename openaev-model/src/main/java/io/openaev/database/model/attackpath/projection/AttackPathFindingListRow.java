package io.openaev.database.model.attackpath.projection;

/**
 * One finding row for the widget drawer list (issue 5048): its id (to fetch the producing
 * executions), its type and value, and its endpoint key. Read paginated, restricted to findings a
 * producing execution links to (the CF1 invariant).
 */
public record AttackPathFindingListRow(String id, String type, String value, String endpointKey) {}
