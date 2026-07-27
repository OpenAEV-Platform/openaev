import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { fetchAttackPathGraph, fetchAttackPathGraphDelta } from '../../../../../actions/attack-path/attack-path-actions';
import type { AttackPathDTO } from '../../../../../utils/api-types';
import { applyDelta, type AttackPathGraphStore, emptyStore, fromSnapshot, toCollapsedDto, toFullDto, withFullSnapshot } from './attack-path-delta-store';

// Delta cadence (issue 6647). Product commits to a committed backend change being visible within 3 s
// p95 during a run; each tick is one indexed point read when nothing changed, so the cost of polling
// this fast is a version comparison, not a graph rebuild.
export const DELTA_POLL_MS = 3000;

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

interface UseAttackPathLiveGraphResult {
  /** Collapsed clustered graph (today's `dto`), stable by reference while nothing changes. */
  dto: AttackPathDTO | null;
  /** Full causal graph (today's `fullDto`), null for runs above the size gate. */
  fullDto: AttackPathDTO | null;
  loading: boolean;
  error: boolean;
  forbidden: boolean;
  freshness: AttackPathFreshness;
  /** When the last successful read landed (ms epoch), for the "reconnecting" hint. */
  lastUpdatedAt: number | null;
  /** Node ids introduced by the most recent delta batch — the entrance affordance's input. */
  newNodeIds: readonly string[];
  /** Finding types touched by the most recent delta batch, for a silent drawer refresh. */
  changedFindingTypes: readonly string[];
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
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [forbidden, setForbidden] = useState(false);
  const [degraded, setDegraded] = useState(false);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<number | null>(null);
  const [batch, setBatch] = useState<{
    newNodeIds: readonly string[];
    changedFindingTypes: readonly string[];
  }>({
    newNodeIds: [],
    changedFindingTypes: [],
  });
  // Bumped on every (re)seed so the poll effect restarts on a fresh cursor; also the monotonic token
  // that drops responses from a superseded simulation or an in-flight resync.
  const [seedNonce, setSeedNonce] = useState(0);
  const seq = useRef(0);
  // The poll reads the cursor from here, not from state, so a tick never closes over a stale version
  // and the timer does not have to be rebuilt after every commit.
  const versionRef = useRef(0);
  // A run that just went terminal still deserves one last read; this makes sure we only do it once.
  const finalReadDone = useRef(false);

  const reseed = useCallback(() => setSeedNonce(n => n + 1), []);

  // Initial read (and every resync): the collapsed snapshot, plus the full one when the run is small
  // enough. Both seed the same store, so the delta poll is the only recurring traffic afterwards.
  useEffect(() => {
    if (!simulationId) {
      storeRef.current = emptyStore();
      setStore(storeRef.current);
      setSeeded(false);
      versionRef.current = 0;
      return;
    }
    seq.current += 1;
    const current = seq.current;
    const isCurrent = () => current === seq.current;
    setLoading(true);
    setError(false);
    setForbidden(false);
    finalReadDone.current = false;
    fetchAttackPathGraph(simulationId, 'collapsed')
      .then(async (collapsed) => {
        if (!isCurrent()) {
          return;
        }
        let next = fromSnapshot(collapsed.data);
        if (fullEligible) {
          // A failed full read is not fatal: the collapsed graph still renders, just without the
          // causal chain overlay (same fallback as before).
          const full = await fetchAttackPathGraph(simulationId, 'full').catch(() => null);
          if (!isCurrent()) {
            return;
          }
          if (full) {
            next = withFullSnapshot(next, full.data);
          }
        }
        versionRef.current = next.version;
        storeRef.current = next;
        setStore(next);
        setSeeded(true);
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
          setLoading(false);
        }
      });
  }, [simulationId, fullEligible, seedNonce]);

  // The delta tick: patch the store, or resync when the backend cannot answer our cursor. Kept in a ref
  // so the interval effect does not restart on each commit.
  const pollRef = useRef<() => Promise<void>>(async () => {});
  pollRef.current = async () => {
    if (!simulationId) {
      return;
    }
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
        setBatch({
          newNodeIds: result.newNodeIds,
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
    }
  };

  // Poll while the run is live and the tab is visible. A terminal run gets one final read; a tab coming
  // back to the foreground gets an immediate catch-up read before the cadence resumes.
  useEffect(() => {
    if (!simulationId || forbidden) {
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
    const start = () => {
      if (timer === undefined) {
        timer = window.setInterval(() => void pollRef.current(), DELTA_POLL_MS);
      }
    };
    const stop = () => {
      if (timer !== undefined) {
        window.clearInterval(timer);
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
  }, [simulationId, terminal, forbidden, seedNonce]);

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
    loading,
    error,
    forbidden,
    freshness,
    lastUpdatedAt,
    newNodeIds: batch.newNodeIds,
    changedFindingTypes: batch.changedFindingTypes,
  };
};

export default useAttackPathLiveGraph;
