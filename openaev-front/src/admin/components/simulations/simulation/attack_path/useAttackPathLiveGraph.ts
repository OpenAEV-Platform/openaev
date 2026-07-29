import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { fetchAttackPathGraph, fetchAttackPathGraphDelta } from '../../../../../actions/attack-path/attack-path-actions';
import type { AttackPathDTO, AttackPathNodeDTO } from '../../../../../utils/api-types';
import { isStreamHealthy, subscribeStreamEvent } from '../../../../../utils/hooks/useDataLoader';
import { applyDelta, type AttackPathGraphStore, emptyStore, fromSnapshot, toCollapsedDto, toFullDto, withFullSnapshot } from './attack-path-delta-store';

// Delta cadence when the stream cannot nudge us (issue 6647). Product commits to a committed backend
// change being visible within 3 s p95 during a run; each tick is one indexed point read when nothing
// changed, so the cost of polling this fast is a version comparison, not a graph rebuild.
export const DELTA_POLL_MS = 3000;

// Cadence while the stream IS delivering (spec 003, FR6): the nudge carries the latency, so polling
// is only a safety net for the events the stream can silently drop (published outside a transaction,
// suppressed during a bulk operation). Jittered so many open views do not converge on the same tick.
export const DELTA_SAFETY_NET_MS = 45000;
export const DELTA_SAFETY_NET_JITTER = 0.1;

// Burst coalescing for the nudge: one ingestion commit can bump several times, and a hot run bumps
// per step event. Debouncing keeps that to one delta read without adding perceptible latency.
export const NUDGE_DEBOUNCE_MS = 300;

// The stream event type the backend publishes on every version bump (StreamApi).
export const ATTACK_PATH_VERSION_EVENT = 'attack-path-version';

// How long a delta batch stays "new" for the caller: slightly above the 420 ms entrance animation, so
// the affordance completes and is then dropped. Without this the last batch would linger forever — a
// remount would replay its animation, and the live region would keep announcing a stale summary.
export const BATCH_TTL_MS = 500;

/** What the freshness indicator shows: updating, degraded but retrying, or done. */
export type AttackPathFreshness = 'live' | 'reconnecting' | 'finished';

interface UseAttackPathLiveGraphParams {
  simulationId: string;
  /**
   * The run is small enough to also seed the full graph (executions + kill-chain fields), which the
   * causal chain layout renders from. False for large runs: they keep the collapsed projection only.
   */
  fullEligible: boolean;
  /** The run reached a terminal status: apply one last delta, then stop polling. */
  terminal: boolean;
}

interface Batch {
  /** Node ids introduced by the batch — the entrance affordance's input. */
  newNodeIds: readonly string[];
  /** The same nodes, resolved against the store, so callers read their kind from `type`. */
  newNodes: readonly AttackPathNodeDTO[];
  /** Finding types touched by the batch, for a silent drawer refresh. */
  changedFindingTypes: readonly string[];
}

const EMPTY_BATCH: Batch = {
  newNodeIds: [],
  newNodes: [],
  changedFindingTypes: [],
};

interface UseAttackPathLiveGraphResult {
  /** Collapsed clustered graph (today's `dto`), stable by reference while nothing changes. */
  dto: AttackPathDTO | null;
  /** Full causal graph (today's `fullDto`), null for runs above the size gate. */
  fullDto: AttackPathDTO | null;
  /**
   * The FIRST load of a simulation is still pending. A resync deliberately does not raise it: the
   * current graph stays mounted while it re-reads, so the user never sees a loader flash or a reset
   * viewport for something they did not ask for.
   */
  loading: boolean;
  error: boolean;
  forbidden: boolean;
  freshness: AttackPathFreshness;
  /** When the last successful read landed (ms epoch), for the "reconnecting" hint. */
  lastUpdatedAt: number | null;
  newNodeIds: readonly string[];
  newNodes: readonly AttackPathNodeDTO[];
  changedFindingTypes: readonly string[];
  /**
   * Bumped by every seed and by every delta that introduced an id — never by an attribute-only tick.
   * Callers gate shape-derived work (kill-chain metadata, layout) on it so a verdict flip does not
   * re-walk the whole graph.
   */
  structuralNonce: number;
  /**
   * The full graph is being merged in and has not settled yet, so a chain-eligible run does not yet know
   * whether it renders the causal chain. Bounded to that one attempt: a failed read settles it too, so a
   * caller gating a loader on it falls back to the aggregated view instead of spinning forever.
   */
  fullPending: boolean;
}

/**
 * One accumulated attack-path graph, live (issue 6647). Reads the snapshot once, then patches it with
 * versioned deltas on a single {@link DELTA_POLL_MS} timer that feeds BOTH render modes — replacing the
 * two 10 s whole-graph polls this view used to run. A delta commit only ever touches graph data:
 * everything the user owns (selection, expansion, drawers, viewport) lives in the caller and is left
 * strictly alone, exactly like the silent poll it replaces.
 *
 * Lifecycle: paused while the tab is hidden (one catch-up read on return), stopped after a final read
 * once the run is terminal, and degraded — last good graph kept, freshness `reconnecting` — on a
 * transient failure, so the view never goes blank or stale-without-saying-so.
 */
const useAttackPathLiveGraph = ({
  simulationId,
  fullEligible,
  terminal,
}: UseAttackPathLiveGraphParams): UseAttackPathLiveGraphResult => {
  const [store, setStore] = useState<AttackPathGraphStore>(emptyStore);
  // The store as the poll sees it: a tick patches the latest committed store without having to be
  // rebuilt whenever it changes (and without a setState updater doing side effects).
  const storeRef = useRef(store);
  // Null until a snapshot landed, so the view can tell "not loaded yet" from "loaded and empty".
  const [seeded, setSeeded] = useState(false);
  // A (re)seed is in flight. The delta poll is suspended for its duration: a tick fired against the
  // pre-seed cursor could answer `resyncRequired` again and start a resync→reseed storm.
  const [seeding, setSeeding] = useState(false);
  const [error, setError] = useState(false);
  const [forbidden, setForbidden] = useState(false);
  const [degraded, setDegraded] = useState(false);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<number | null>(null);
  const [batch, setBatch] = useState<Batch>(EMPTY_BATCH);
  const [structuralNonce, setStructuralNonce] = useState(0);
  // Whether the full read has settled for this simulation (merged, or failed and given up). Only the
  // window before it settles is "pending"; after that the view commits to whichever mode it can render.
  const [fullSettled, setFullSettled] = useState(false);
  // Bumped on every resync so the seed effect re-runs on a fresh cursor; also the monotonic token
  // that drops responses from a superseded simulation or an in-flight resync.
  const [seedNonce, setSeedNonce] = useState(0);
  const seq = useRef(0);
  // The poll reads the cursor from here, not from state, so a tick never closes over a stale version
  // and the timer does not have to be rebuilt after every commit.
  const versionRef = useRef(0);
  // A run that just went terminal still deserves one last read; this makes sure we only do it once.
  const finalReadDone = useRef(false);

  const reseed = useCallback(() => setSeedNonce(n => n + 1), []);

  // A genuine simulation switch is the only thing that unseeds the view: the loader belongs to a run
  // the user picked, never to a resync of the run they are already looking at.
  useEffect(() => {
    setSeeded(false);
    setFullSettled(false);
  }, [simulationId]);

  // Initial read (and every resync): the collapsed snapshot, which seeds the store and the cursor from
  // its own `graphVersion`. The full graph is merged in separately (below) so its availability can flip
  // without re-reading — or resetting — anything.
  useEffect(() => {
    if (!simulationId) {
      // Bump the token BEFORE clearing: a snapshot still in flight for the previous simulation would
      // otherwise land after the reset and repopulate a view that has nothing selected.
      seq.current += 1;
      storeRef.current = emptyStore();
      setStore(storeRef.current);
      setSeeding(false);
      versionRef.current = 0;
      return;
    }
    seq.current += 1;
    const current = seq.current;
    const isCurrent = () => current === seq.current;
    setSeeding(true);
    setError(false);
    setForbidden(false);
    finalReadDone.current = false;
    fetchAttackPathGraph(simulationId, 'collapsed')
      .then((collapsed) => {
        if (!isCurrent()) {
          return;
        }
        const next = fromSnapshot(collapsed.data);
        versionRef.current = next.version;
        storeRef.current = next;
        setStore(next);
        setSeeded(true);
        setStructuralNonce(n => n + 1);
        setDegraded(false);
        setLastUpdatedAt(Date.now());
      })
      .catch((err: { status?: number }) => {
        if (!isCurrent()) {
          return;
        }
        // Drop the previous simulation's graph so a failed load shows an error, never stale data.
        storeRef.current = emptyStore();
        setStore(storeRef.current);
        setSeeded(false);
        versionRef.current = 0;
        if (err?.status === 403) {
          setForbidden(true);
        } else {
          setError(true);
        }
      })
      .finally(() => {
        if (isCurrent()) {
          setSeeding(false);
        }
      });
  }, [simulationId, seedNonce]);

  // The full graph, merged into the already-seeded store. Kept in its own effect so `fullEligible`
  // flipping (it resolves from the picker list, which lands after the first read) MERGES the causal
  // fields in rather than re-seeding: no second collapsed read, no cursor reset, and no delta applied
  // in between is discarded. A failed read is not fatal — the collapsed graph still renders, just
  // without the causal overlay.
  useEffect(() => {
    if (!simulationId || !fullEligible || !seeded || store.hasFull) {
      return;
    }
    const current = seq.current;
    fetchAttackPathGraph(simulationId, 'full')
      .then((full) => {
        if (current !== seq.current) {
          return;
        }
        const next = withFullSnapshot(storeRef.current, full.data);
        storeRef.current = next;
        setStore(next);
        setStructuralNonce(n => n + 1);
        setFullSettled(true);
      })
      .catch(() => {
        // No overlay for this run; the collapsed projection is unaffected. Settle anyway so a caller
        // waiting on the causal chain stops waiting and renders the aggregated view.
        if (current === seq.current) {
          setFullSettled(true);
        }
      });
  }, [simulationId, fullEligible, seeded, store.hasFull]);

  // The delta tick: patch the store, or resync when the backend cannot answer our cursor.
  // Never runs twice concurrently: the nudge, the cadence timer and the visibility catch-up all call
  // in here, and two overlapping reads would fetch from the same cursor and re-emit the same batch —
  // replaying the entrance animation and re-announcing the live region. A call arriving during a read
  // sets `pollQueued`, which the in-flight one drains exactly once.
  const inFlight = useRef(false);
  const pollQueued = useRef(false);
  // Declared before `poll` so the drain at the end of a read can call the latest one without a
  // use-before-define cycle; kept in sync by the effect below.
  const pollRef = useRef<() => Promise<void>>(async () => {});
  const poll = useCallback(async () => {
    if (!simulationId) {
      return;
    }
    if (inFlight.current) {
      pollQueued.current = true;
      return;
    }
    inFlight.current = true;
    const current = seq.current;
    try {
      const { data } = await fetchAttackPathGraphDelta(simulationId, versionRef.current);
      if (current !== seq.current) {
        return;
      }
      if (data.resyncRequired) {
        reseed();
        return;
      }
      setDegraded(false);
      setLastUpdatedAt(Date.now());
      const result = applyDelta(storeRef.current, data);
      // The cursor advances on every tick; the rendered graph is only committed when something
      // actually changed, so a steady-state run costs one request and zero renders.
      versionRef.current = result.store.version;
      if (result.changed) {
        storeRef.current = result.store;
        setStore(result.store);
        if (result.structuralChange) {
          setStructuralNonce(n => n + 1);
        }
        setBatch({
          newNodeIds: result.newNodeIds,
          // Resolved from the store the batch produced, so callers read a node's kind from its `type`
          // instead of pattern-matching its id.
          newNodes: result.newNodeIds
            .map(id => result.store.nodes.get(id) ?? result.store.executions.get(id))
            .filter((n): n is AttackPathNodeDTO => !!n),
          changedFindingTypes: result.changedFindingTypes,
        });
      }
    } catch (err) {
      if (current !== seq.current) {
        return;
      }
      if ((err as { status?: number })?.status === 403) {
        // Permission was revoked mid-run: stop updating and surface it, like the snapshot read does.
        setForbidden(true);
        return;
      }
      // Keep the last good graph and say so; the next tick retries.
      setDegraded(true);
    } finally {
      inFlight.current = false;
      if (pollQueued.current) {
        pollQueued.current = false;
        void pollRef.current();
      }
    }
  }, [simulationId, reseed]);
  // The interval calls through the ref so the timer is not torn down and rebuilt whenever `poll` is
  // rebuilt; the ref is synced in an effect rather than during render.
  useEffect(() => {
    pollRef.current = poll;
  }, [poll]);

  // Poll while the run is live and the tab is visible. A terminal run gets one final read; a tab coming
  // back to the foreground gets an immediate catch-up read before the cadence resumes.
  useEffect(() => {
    if (!simulationId || forbidden || seeding) {
      return undefined;
    }
    if (terminal) {
      if (!finalReadDone.current) {
        finalReadDone.current = true;
        void pollRef.current();
      }
      return undefined;
    }
    let timer: number | undefined;
    // The cadence follows the stream: a healthy stream nudges us, so the timer is only the safety net
    // for the drops it cannot signal; a dead stream (or an environment with no EventSource at all)
    // restores the 3 s cadence, which is the shipped behaviour of spec 002.
    const period = () => {
      if (!isStreamHealthy()) {
        return DELTA_POLL_MS;
      }
      const jitter = 1 + (Math.random() * 2 - 1) * DELTA_SAFETY_NET_JITTER;
      return Math.round(DELTA_SAFETY_NET_MS * jitter);
    };
    const start = () => {
      if (timer === undefined) {
        // setTimeout rather than setInterval: the period is re-evaluated on every tick, so a stream
        // that dies mid-run tightens the net without waiting for a 45 s interval to elapse.
        const tick = () => {
          void pollRef.current();
          timer = window.setTimeout(tick, period());
        };
        timer = window.setTimeout(tick, period());
      }
    };
    const stop = () => {
      if (timer !== undefined) {
        window.clearTimeout(timer);
        timer = undefined;
      }
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        stop();
      } else {
        void pollRef.current();
        start();
      }
    };
    if (document.visibilityState !== 'hidden') {
      start();
    }
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      stop();
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, [simulationId, terminal, forbidden, seeding]);

  // The nudge (spec 003, FR1/FR2): the backend announces that this simulation's version moved, and we
  // fetch the delta immediately instead of waiting for the safety net. The event carries no graph
  // data, so a lost, duplicated or reordered one is harmless — the cursor makes the fetch idempotent.
  // Only this simulation's events matter, which is also what makes the scenario picker work: switching
  // simulation re-runs this effect and the filter follows.
  useEffect(() => {
    if (!simulationId || forbidden || terminal) {
      return undefined;
    }
    let debounce: number | undefined;
    const unsubscribe = subscribeStreamEvent(ATTACK_PATH_VERSION_EVENT, (event: MessageEvent) => {
      let payload: { simulation_id?: string };
      try {
        payload = JSON.parse(event.data);
      } catch {
        return; // a malformed nudge costs nothing: the safety net still runs
      }
      if (payload.simulation_id !== simulationId) {
        return;
      }
      window.clearTimeout(debounce);
      debounce = window.setTimeout(() => void pollRef.current(), NUDGE_DEBOUNCE_MS);
    });
    return () => {
      window.clearTimeout(debounce);
      unsubscribe();
    };
  }, [simulationId, forbidden, terminal]);

  // A batch is transient: it drives a one-shot entrance animation and a one-shot announcement, so it is
  // dropped once both have had time to play. Cancelled by the next batch and on unmount.
  useEffect(() => {
    if (batch === EMPTY_BATCH) {
      return undefined;
    }
    const timer = window.setTimeout(() => setBatch(EMPTY_BATCH), BATCH_TTL_MS);
    return () => window.clearTimeout(timer);
  }, [batch]);

  const dto = useMemo(() => (seeded ? toCollapsedDto(store) : null), [store, seeded]);
  const fullDto = useMemo(() => toFullDto(store), [store]);
  const freshness: AttackPathFreshness = (() => {
    if (degraded) {
      return 'reconnecting';
    }
    return terminal ? 'finished' : 'live';
  })();

  return {
    dto,
    fullDto,
    loading: seeding && !seeded,
    error,
    forbidden,
    freshness,
    lastUpdatedAt,
    newNodeIds: batch.newNodeIds,
    newNodes: batch.newNodes,
    changedFindingTypes: batch.changedFindingTypes,
    structuralNonce,
    fullPending: fullEligible && !fullSettled && !store.hasFull,
  };
};

export default useAttackPathLiveGraph;
