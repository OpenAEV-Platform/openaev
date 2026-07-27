import type { AttackPathCounters, AttackPathDTO, AttackPathEdges, AttackPathNodeDTO } from '../../../../../utils/api-types';
import type { AttackPathDeltaDTO } from '../../../../../utils/api-types-custom';

// Accumulated attack-path graph (issue 6647). A run keeps discovering endpoints, executions and
// findings, so the view is fed by ONE initial snapshot plus versioned deltas instead of re-reading the
// whole graph on a timer. This module is the pure part of that: an id-indexed store, an idempotent
// upsert reducer, and the two projections the view already consumes (`dto` = collapsed clustered
// graph, `fullDto` = full causal chain). No React, no fetching — see `useAttackPathLiveGraph`.

// Node/edge type discriminants, mirroring the backend's AttackPathGraphService constants.
const TYPE_FINDING = 'FINDING';
const EDGE_EXECUTIONS = 'EDGE_EXECUTIONS';

// The collapsed projection is exactly the endpoint/injector topology: the clustered layout reads only
// these node and edge kinds, so restricting them keeps `dto` identical to today's collapsed read even
// once the store also holds the full graph's finding nodes.
const COLLAPSED_NODE_TYPES = new Set(['ASSET', 'INJECTOR']);

/**
 * The accumulated graph. Maps are keyed by the backend's deterministic ids, so a delta is a pure
 * upsert and applying it twice is a no-op (FR4). Entry objects are treated as immutable: an untouched
 * entry keeps its object identity across delta applications, which is what lets React Flow keep node
 * positions and skip re-renders (FR7).
 */
export interface AttackPathGraphStore {
  /** Backend graph version the store is caught up to; the cursor of the next delta poll. */
  version: number;
  /** True once a full-mode snapshot has seeded the store (small runs only). */
  hasFull: boolean;
  nodes: ReadonlyMap<string, AttackPathNodeDTO>;
  edges: ReadonlyMap<string, AttackPathEdges>;
  /** Full-mode execution feed nodes (EXECUTION), keyed by node id. */
  executions: ReadonlyMap<string, AttackPathNodeDTO>;
  /** Deduplicated finding nodes, mirroring the snapshot's `staticAttackPathFindings`. */
  staticFindings: ReadonlyMap<string, AttackPathNodeDTO>;
  counters?: AttackPathCounters;
}

/** Outcome of applying one delta batch. */
export interface AttackPathDeltaResult {
  /** The next store — the SAME object when neither the entities nor the version moved. */
  store: AttackPathGraphStore;
  /**
   * An entity or a counter actually changed. False on the steady-state tick (nothing to show), which
   * lets the caller skip the commit entirely and leave the rendered graph untouched.
   */
  changed: boolean;
  /**
   * A node or edge id appeared (or an execution entered the feed): the layout has to be recomputed.
   * False for attribute-only changes (a verdict, a status, a recomputed count), which mutate node data
   * under a stable identity so positions and the viewport are left alone.
   */
  structuralChange: boolean;
  /** Node ids that did not exist before this batch — the entrance affordance's input (FR13). */
  newNodeIds: string[];
  /** Finding types touched by this batch, so an open findings drawer can refresh silently. */
  changedFindingTypes: string[];
}

/** How one upsert ended, so a batch can tell a new id from a changed attribute from a no-op. */
type UpsertOutcome = 'added' | 'updated' | 'unchanged';
type TrackFn = (outcome: UpsertOutcome, id?: string) => void;

export const emptyStore = (): AttackPathGraphStore => ({
  version: 0,
  hasFull: false,
  nodes: new Map(),
  edges: new Map(),
  executions: new Map(),
  staticFindings: new Map(),
});

// Structural value equality over the plain JSON the API returns (objects, arrays, primitives). Used
// per entity, so an unchanged entry can keep its object identity instead of being replaced by an equal
// copy — the whole point of the store (and the reason the old whole-payload JSON.stringify diff is
// gone: this compares one node, not the graph).
const sameValue = (a: unknown, b: unknown): boolean => {
  if (a === b) {
    return true;
  }
  if (a === null || b === null || a === undefined || b === undefined) {
    return false;
  }
  if (Array.isArray(a) || Array.isArray(b)) {
    if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) {
      return false;
    }
    return a.every((item, i) => sameValue(item, b[i]));
  }
  if (typeof a === 'object' && typeof b === 'object') {
    const left = a as Record<string, unknown>;
    const right = b as Record<string, unknown>;
    const keys = new Set([...Object.keys(left), ...Object.keys(right)]);
    return [...keys].every(k => sameValue(left[k], right[k]));
  }
  return false;
};

// One upsert into a working map. Merging (rather than replacing) matters because the collapsed and
// full projections describe the same node/edge id from two angles — the collapsed ASSET node carries
// `findingCounts`, the full one carries the causal fields — and a delta ships one of the two. Merging
// stays idempotent: re-applying the same entry yields the same merged value, and when the merge is
// value-equal to what is stored the EXISTING object is kept so its identity survives.
const upsert = <T extends object>(
  target: Map<string, T>,
  id: string | undefined,
  incoming: T,
  merge?: (existing: T, next: T) => T,
): UpsertOutcome => {
  if (!id) {
    return 'unchanged';
  }
  const existing = target.get(id);
  if (!existing) {
    target.set(id, incoming);
    return 'added';
  }
  const merged = merge
    ? merge(existing, incoming)
    : {
        ...existing,
        ...incoming,
      };
  if (sameValue(existing, merged)) {
    return 'unchanged';
  }
  target.set(id, merged);
  return 'updated';
};

// An execution edge's `executionIds` accumulates: the delta only ships the refs of the executions that
// changed, while the collapsed variant of the same edge ships the recomputed `count` and no refs. The
// full causal layout resolves each execution's injector/endpoint through this list, so dropping the
// known refs would make already-rendered executions disappear.
const mergeEdge = (existing: AttackPathEdges, incoming: AttackPathEdges): AttackPathEdges => {
  const merged = {
    ...existing,
    ...incoming,
  };
  const known = existing.executionIds;
  if (!known || known.length === 0) {
    return merged;
  }
  const added = (incoming.executionIds ?? []).filter(id => !known.includes(id));
  merged.executionIds = added.length > 0 ? [...known, ...added] : known;
  return merged;
};

const indexBy = <T>(entries: T[] | undefined, key: (entry: T) => string | undefined): ReadonlyMap<string, T> => {
  const map = new Map<string, T>();
  (entries ?? []).forEach((entry) => {
    const id = key(entry);
    if (id) {
      map.set(id, entry);
    }
  });
  return map;
};

const indexById = (entries: AttackPathNodeDTO[] | undefined): ReadonlyMap<string, AttackPathNodeDTO> =>
  indexBy(entries, n => n.id);

// Derived projections are memoized per store object: a store is immutable and only replaced when a
// delta actually changed something, so an unchanged tick hands the view the very same DTO reference
// and every downstream useMemo (layout, kill-chain meta, table rows) short-circuits.
interface Projections {
  collapsed?: AttackPathDTO;
  full?: AttackPathDTO | null;
}
const projections = new WeakMap<AttackPathGraphStore, Projections>();
const memoOf = (store: AttackPathGraphStore): Projections => {
  let memo = projections.get(store);
  if (!memo) {
    memo = {};
    projections.set(store, memo);
  }
  return memo;
};
const carryProjections = (from: AttackPathGraphStore, to: AttackPathGraphStore) => {
  const memo = projections.get(from);
  if (memo) {
    projections.set(to, { ...memo });
  }
};

// Nothing changed but the cursor: hand back a store carrying the new version while keeping every
// collection — and the memoized projections built from them — identical.
const unchangedResult = (store: AttackPathGraphStore, version: number): AttackPathDeltaResult => {
  const next = version === store.version
    ? store
    : {
        ...store,
        version,
      };
  if (next !== store) {
    carryProjections(store, next);
  }
  return {
    store: next,
    changed: false,
    structuralChange: false,
    newNodeIds: [],
    changedFindingTypes: [],
  };
};

/**
 * Seed the store from a collapsed snapshot (the resync / initial read). `version` is the backend graph
 * version the snapshot reflects; the snapshot endpoint does not expose it yet, so callers seed 0 and
 * the first delta replays the whole graph once — idempotently (TODO(#6647): return the version from
 * the snapshot read and drop that first replay).
 */
export const fromSnapshot = (dto: AttackPathDTO | null | undefined, version = 0): AttackPathGraphStore => {
  const store = emptyStore();
  if (!dto) {
    return {
      ...store,
      version,
    };
  }
  return {
    ...store,
    version,
    nodes: indexById(dto.attackPathNodes),
    edges: indexBy(dto.attackPathEdges, e => e.edgeId),
    counters: dto.counters,
  };
};

/**
 * Merge a full-mode snapshot into the store: the causal chain needs the per-execution kill-chain
 * fields, the finding nodes and the finding edges, which the collapsed read omits. Only seeded for
 * runs under the size gate; large runs keep serving the collapsed projection alone.
 */
export const withFullSnapshot = (
  store: AttackPathGraphStore,
  dto: AttackPathDTO | null | undefined,
): AttackPathGraphStore => {
  if (!dto) {
    return store;
  }
  const nodes = new Map(store.nodes);
  const edges = new Map(store.edges);
  (dto.attackPathNodes ?? []).forEach(n => upsert(nodes, n.id, n));
  (dto.attackPathEdges ?? []).forEach(e => upsert(edges, e.edgeId, e, mergeEdge));
  return {
    ...store,
    hasFull: true,
    nodes,
    edges,
    executions: indexById(dto.attackPathExecutions),
    staticFindings: indexById(dto.staticAttackPathFindings),
    counters: dto.counters ?? store.counters,
  };
};

/**
 * Apply one delta batch. Pure: the store is never mutated, and when nothing actually changed the very
 * same store object is returned so every derived projection keeps its identity. A `resyncRequired`
 * delta carries no entities — the caller re-seeds from a snapshot instead of patching.
 */
export const applyDelta = (
  store: AttackPathGraphStore,
  delta: AttackPathDeltaDTO,
): AttackPathDeltaResult => {
  const version = delta.newVersion ?? store.version;
  if (delta.resyncRequired) {
    return unchangedResult(store, store.version);
  }

  const nodes = new Map(store.nodes);
  const edges = new Map(store.edges);
  const executions = new Map(store.executions);
  const staticFindings = new Map(store.staticFindings);
  const newNodeIds: string[] = [];
  const changedFindingTypes = new Set<string>();
  let structuralChange = false;
  let touched = false;

  const track: TrackFn = (outcome, id) => {
    if (outcome === 'unchanged') {
      return;
    }
    touched = true;
    if (outcome === 'added') {
      structuralChange = true;
      if (id) {
        newNodeIds.push(id);
      }
    }
  };

  // The delta is built by the very same pass as the snapshot, over the changed rows only, so its node
  // list already carries every kind (endpoints, injectors, finding types, findings) with the
  // aggregates recomputed whole — upserting it is the entire reducer.
  const noteFindingType = (node: AttackPathNodeDTO) => {
    if (node.type === TYPE_FINDING && node.typeFindings) {
      changedFindingTypes.add(node.typeFindings);
    }
  };
  (delta.attackPathNodes ?? []).forEach((n) => {
    track(upsert(nodes, n.id, n), n.id);
    noteFindingType(n);
  });
  (delta.attackPathEdges ?? []).forEach(e => track(upsert(edges, e.edgeId, e, mergeEdge)));
  // Feed entries only exist in the full projection; when it was never seeded (run above the size gate)
  // they are still accumulated so a later render-mode switch has them, at no layout cost.
  (delta.attackPathExecutions ?? []).forEach(e => track(upsert(executions, e.id, e), e.id));
  (delta.staticAttackPathFindings ?? []).forEach((f) => {
    upsert(staticFindings, f.id, f);
    track(upsert(nodes, f.id, f), f.id);
    noteFindingType(f);
  });

  const countersChanged = delta.counters !== undefined && !sameValue(store.counters, delta.counters);
  if (!touched && !countersChanged) {
    return unchangedResult(store, version);
  }

  return {
    store: {
      version,
      hasFull: store.hasFull,
      nodes,
      edges,
      executions,
      staticFindings,
      counters: countersChanged ? delta.counters : store.counters,
    },
    changed: true,
    structuralChange,
    newNodeIds,
    changedFindingTypes: [...changedFindingTypes],
  };
};

/** The collapsed clustered graph — today's `dto`: endpoint/injector topology plus the counters. */
export const toCollapsedDto = (store: AttackPathGraphStore): AttackPathDTO => {
  const memo = memoOf(store);
  if (!memo.collapsed) {
    memo.collapsed = {
      mode: 'collapsed',
      attackPathNodes: [...store.nodes.values()].filter(n => COLLAPSED_NODE_TYPES.has(n.type ?? '')),
      attackPathEdges: [...store.edges.values()].filter(e => e.type === EDGE_EXECUTIONS),
      attackPathExecutions: [],
      staticAttackPathFindings: [],
      counters: store.counters,
    };
  }
  return memo.collapsed;
};

/**
 * The full causal graph — today's `fullDto`: every node kind, every edge kind and the execution feed
 * with its kill-chain fields. Null until a full snapshot seeded the store (runs above the size gate
 * never fetch one), which is exactly the condition today's `chainMode` tests.
 */
export const toFullDto = (store: AttackPathGraphStore): AttackPathDTO | null => {
  const memo = memoOf(store);
  if (memo.full === undefined) {
    memo.full = store.hasFull
      ? {
          mode: 'full',
          attackPathNodes: [...store.nodes.values()],
          attackPathEdges: [...store.edges.values()],
          attackPathExecutions: [...store.executions.values()],
          staticAttackPathFindings: [...store.staticFindings.values()],
          counters: store.counters,
        }
      : null;
  }
  return memo.full;
};
