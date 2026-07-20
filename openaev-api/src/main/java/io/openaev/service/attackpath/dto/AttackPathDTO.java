package io.openaev.service.attackpath.dto;

import java.util.List;

/**
 * The attack-path render contract for one simulation (issue 6647), named after the design's {@code
 * AttackPathDTO}. Separate lists per UI zone (deliberate redundancy so the front never re-filters),
 * all produced in the same in-memory pass:
 *
 * <ul>
 *   <li>{@code staticAttackPathFindings} — finding nodes for the top bar and right drawer.
 *   <li>{@code attackPathExecutions} — the left execution feed.
 *   <li>{@code attackPathNodes} — every node for the map (injector, asset, finding type, finding).
 *   <li>{@code attackPathEdges} — the edges.
 *   <li>{@code counters} — top-bar counts (D2).
 *   <li>{@code mode} — {@code full} (every node) or {@code collapsed} (DB-aggregated: injector and
 *       endpoint nodes with per-type finding counts, grouped edges, counters; the per-execution and
 *       per-finding lists are empty, and detail is loaded on click). See ADR-003.
 * </ul>
 */
public record AttackPathDTO(
    List<AttackPathNodeDTO> staticAttackPathFindings,
    List<AttackPathNodeDTO> attackPathExecutions,
    List<AttackPathNodeDTO> attackPathNodes,
    List<AttackPathEdges> attackPathEdges,
    AttackPathCounters counters,
    String mode) {}
