package io.openaev.service.attackpath.dto;

import java.util.List;

/**
 * What changed in a simulation's attack path since a client's version (#6647, spec 002). Every
 * collection is a set of <b>upserts keyed by the same stable node/edge id the snapshot uses</b>, so
 * a client applies a delta by replacing entries in a map: applying one twice, or in any order
 * relative to a resync, lands on the same state as a fresh full load (FR4).
 *
 * <p>The entity lists are built by the very same pass that builds the snapshot, over the changed
 * rows only, so the payload's field set is a strict subset of the snapshot's — a new field can
 * never leak through the delta without also appearing in the snapshot (FR6). Aggregates that a
 * subset of rows cannot compute correctly (an endpoint's worst-case colour, its per-type finding
 * counts, an edge's execution count) are recomputed over all of the affected endpoints' rows and
 * shipped whole, never as increments (FR1).
 *
 * <p>{@code resyncRequired} is the explicit "your cursor is not answerable" signal (FR3): the
 * cursor is ahead of the current version (the simulation was reset and re-seeded), the simulation
 * has no version at all while the client claims one (its attack path was deleted), or too much
 * changed to be worth expressing as a delta. When it is true every collection is empty and the
 * client re-reads the snapshot; there is no partial or half-applied middle ground.
 *
 * <p>A null {@code counters} means "unchanged, keep the ones you have". Only a tick that carries
 * entities recomputes them, so the steady state of a quiet run costs one indexed point read per
 * poll rather than two aggregate queries.
 *
 * <p>Field names and order mirror {@link AttackPathDTO} on purpose, so the front feeds both through
 * one reducer.
 */
public record AttackPathDeltaDTO(
    long sinceVersion,
    long newVersion,
    boolean resyncRequired,
    List<AttackPathNodeDTO> staticAttackPathFindings,
    List<AttackPathNodeDTO> attackPathExecutions,
    List<AttackPathNodeDTO> attackPathNodes,
    List<AttackPathEdges> attackPathEdges,
    AttackPathCounters counters) {

  /** An answerable cursor with nothing behind it: the steady state while a run is quiet. */
  public static AttackPathDeltaDTO empty(long since, long current) {
    return new AttackPathDeltaDTO(
        since, current, false, List.of(), List.of(), List.of(), List.of(), null);
  }

  /** "Re-read the snapshot": no entities, and the version the client should expect to land on. */
  public static AttackPathDeltaDTO resync(long since, long current) {
    return new AttackPathDeltaDTO(
        since, current, true, List.of(), List.of(), List.of(), List.of(), null);
  }
}
