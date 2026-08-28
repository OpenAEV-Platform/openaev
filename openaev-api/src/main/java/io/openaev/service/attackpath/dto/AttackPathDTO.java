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
 *   <li>{@code graphVersion} — the simulation's attack-path version this snapshot was assembled at,
 *       0 when the simulation has no attack-path data. It is the cursor the client then polls the
 *       delta endpoint with (#6647, spec 002); without it a fresh snapshot could only start from 0
 *       and would re-receive the whole graph as its first delta.
 * </ul>
 *
 * <p>The version is read BEFORE the rows, in the same read-only transaction. That order is the safe
 * one: a write committing in between makes the client refetch rows it already has on its first
 * delta (idempotent upserts, so harmless), whereas reading the version after the rows would let
 * that write fall in the gap and never reach the client.
 */
public record AttackPathDTO(
    List<AttackPathNodeDTO> staticAttackPathFindings,
    List<AttackPathNodeDTO> attackPathExecutions,
    List<AttackPathNodeDTO> attackPathNodes,
    List<AttackPathEdges> attackPathEdges,
    AttackPathCounters counters,
    String mode,
    long graphVersion) {}
