import { focusManager, type Query, type QueryClient } from '@tanstack/react-query';

import { type EntityChange, entityRefKey } from './entityChange';
import { type LiveMeta } from './live';

/** Changes received within this window are routed together. */
const BATCH_WINDOW_MS = 250;
const DEFAULT_THROTTLE_MS = 1000;

export interface ApplyOptions {
  /**
   * Refetch the matching queries right away, outside the batch window and the throttle, and resolve
   * once they are refetched: the user's own mutation must show at once.
   */
  immediate?: boolean;
}

export interface LiveRouter {
  /**
   * Describes entity changes (from the stream or a mutation): matching live queries are invalidated.
   * Resolves once the immediate refetches settle (at once for batched changes).
   */
  applyChanges: (changes: EntityChange[], options?: ApplyOptions) => Promise<void>;
  /** Invalidates every live query (e.g. after a (re)connection or a bulk operation). */
  invalidateAllLive: () => void;
  /**
   * Invalidates every query, live or not, restarting fetches in flight: the user's rights grew, so
   * any query may now return more.
   */
  invalidateAll: () => Promise<void>;
  /**
   * Drops every query's data, cancelling fetches in flight, and refetches the mounted ones: the
   * user's rights shrank, so cached data may no longer be theirs to see.
   */
  resetAll: () => Promise<void>;
  /** Drops the buffered changes, pending timers and cache subscription. The router stays usable afterwards. */
  dispose: () => void;
}

interface ThrottleState {
  lastRun: number;
  timer?: ReturnType<typeof setTimeout>;
}

/** The changes of one flush, indexed once for every query to match against. */
interface ChangeIndex {
  keys: Set<string>;
  // Matched against `collections`: a created or deleted entity can enter or leave a list and
  // changes its total, even when the query does not show it.
  createdOrDeletedTypes: Set<string>;
  // Matched against `watch`: any change of the type.
  types: Set<string>;
}

type LiveQuery = Query & { meta: { live: LiveMeta } };

const NO_REFS: ReadonlySet<string> = new Set();

const isLiveQuery = (query: Query): query is LiveQuery => query.meta?.live !== undefined;

// A fetch in flight (or paused while offline) will replace the data: its response may predate the change.
const isFetching = (query: Query) => query.state.fetchStatus !== 'idle';

const reportRefsError = (query: Query, error: unknown) => {
  if (import.meta.env.DEV && import.meta.env.MODE !== 'test') {
    // eslint-disable-next-line no-console
    console.error('[live] refs failed: the query only reacts to its collections and watched types', query.queryKey, error);
  }
};

// Refs are computed once per fetched data, not on every flush. Keyed by the data only: a query's
// `refs` is a new closure on every render, but always describes the same data the same way.
const refKeysByData = new WeakMap<object, ReadonlySet<string>>();

const refKeysOf = (query: LiveQuery): ReadonlySet<string> => {
  const { live } = query.meta;
  const { data } = query.state;
  if (!live.refs || data === undefined) return NO_REFS;
  const memoizable = typeof data === 'object' && data !== null;
  const known = memoizable ? refKeysByData.get(data) : undefined;
  if (known !== undefined) return known;
  let index = NO_REFS;
  try {
    const refs = live.refs(data);
    if (Array.isArray(refs)) {
      index = new Set(refs.map(entityRefKey));
    }
  } catch (error) {
    reportRefsError(query, error);
  }
  if (memoizable) refKeysByData.set(data, index);
  return index;
};

/** Each change also updates its parents; a parent carries no action of its own. */
const expand = (changes: EntityChange[]): EntityChange[] =>
  changes.flatMap(change => [change, ...(change.parents ?? []).map(parent => ({
    ...parent,
    action: 'update' as const,
  }))]);

const indexChanges = (changes: EntityChange[]): ChangeIndex => ({
  keys: new Set(changes.map(entityRefKey)),
  createdOrDeletedTypes: new Set(changes.filter(change => change.action !== 'update').map(change => change.type)),
  types: new Set(changes.map(change => change.type)),
});

const copyIndex = (index: ChangeIndex): ChangeIndex => ({
  keys: new Set(index.keys),
  createdOrDeletedTypes: new Set(index.createdOrDeletedTypes),
  types: new Set(index.types),
});

const mergeInto = (target: ChangeIndex, index: ChangeIndex) => {
  index.keys.forEach(key => target.keys.add(key));
  index.createdOrDeletedTypes.forEach(type => target.createdOrDeletedTypes.add(type));
  index.types.forEach(type => target.types.add(type));
};

// Two changes with the same key route the same way: only one is kept while a batch fills up.
const changeKey = (change: EntityChange) =>
  `${entityRefKey(change)}|${change.action}|${(change.parents ?? []).map(entityRefKey).join(',')}`;

/**
 * A query matches any change of a type it watches (`watch`), a create/delete of a type it lists
 * (`collections`), both even before its first data, or a change of an entity its data shows (any
 * action).
 */
const matches = (query: LiveQuery, index: ChangeIndex) => {
  const { watch, collections } = query.meta.live;
  if (watch?.some(type => index.types.has(type))) return true;
  if (collections?.some(type => index.createdOrDeletedTypes.has(type))) return true;
  const keys = refKeysOf(query);
  const [smaller, larger] = keys.size < index.keys.size ? [keys, index.keys] : [index.keys, keys];
  return [...smaller].some(key => larger.has(key));
};

export const createLiveRouter = (queryClient: QueryClient): LiveRouter => {
  // Bounded by the distinct entities changed within a window, not by the raw event volume (a hidden
  // tab only flushes once a minute).
  const buffer = new Map<string, EntityChange>();
  let batchTimer: ReturnType<typeof setTimeout> | undefined;
  const throttles = new Map<string, ThrottleState>();
  // Changes received while a query was fetching, matched against the data that fetch returns.
  // Kept as an index: bounded by the distinct entities changed during the fetch.
  const parked = new Map<string, ChangeIndex>();
  // Queries due for an invalidation while a fetch was in flight: run once that fetch settles.
  const afterFetch = new Set<string>();
  let unsubscribeCache: (() => void) | undefined;

  const liveQueries = () => queryClient.getQueryCache().getAll().filter(isLiveQuery);

  // A tab in the background only marks its queries stale: TanStack refetches them on focus. Only
  // settled queries get here, so there is no fetch in flight to cancel.
  const invalidateReady = (hashes: Set<string>) => {
    if (hashes.size === 0) return Promise.resolve();
    return queryClient.invalidateQueries({
      predicate: query => hashes.has(query.queryHash),
      refetchType: focusManager.isFocused() ? 'active' : 'none',
    });
  };

  const throttleOf = (hash: string) => {
    const state = throttles.get(hash) ?? { lastRun: Number.NEGATIVE_INFINITY };
    throttles.set(hash, state);
    return state;
  };

  const clearPending = (hash: string) => {
    const state = throttles.get(hash);
    if (state?.timer !== undefined) {
      clearTimeout(state.timer);
      state.timer = undefined;
    }
    afterFetch.delete(hash);
    parked.delete(hash);
  };

  const park = (hash: string, index: ChangeIndex) => {
    const parkedIndex = parked.get(hash);
    if (parkedIndex === undefined) parked.set(hash, copyIndex(index));
    else mergeInto(parkedIndex, index);
  };

  // eslint-disable-next-line prefer-const -- assigned below, used by the cache subscription
  let throttled: (queries: LiveQuery[]) => void;

  const ensureCacheSubscription = () => {
    if (unsubscribeCache !== undefined) return;
    unsubscribeCache = queryClient.getQueryCache().subscribe((event) => {
      const { query } = event;
      const hash = query.queryHash;
      if (event.type === 'removed') {
        clearPending(hash);
        throttles.delete(hash);
        return;
      }
      if (event.type !== 'updated' || isFetching(query)) return;
      const due = afterFetch.delete(hash);
      const parkedIndex = parked.get(hash);
      parked.delete(hash);
      if (!isLiveQuery(query)) return;
      if (due) {
        // Already throttled when it was decided: the fetch that just settled predates the change.
        throttleOf(hash).lastRun = Date.now();
        invalidateReady(new Set([hash]));
      } else if (parkedIndex !== undefined && matches(query, parkedIndex)) {
        // The settled data is what the user sees now: only the parked changes it is concerned by
        // need another fetch, through the throttle like any other change.
        throttled([query]);
      }
    });
  };

  const invalidateNow = (hashes: Set<string>) => {
    const ready = new Set<string>();
    hashes.forEach((hash) => {
      const query = queryClient.getQueryCache().get(hash);
      if (query === undefined) return;
      if (isFetching(query)) {
        ensureCacheSubscription();
        afterFetch.add(hash);
      } else {
        ready.add(hash);
      }
    });
    return invalidateReady(ready);
  };

  // Leading edge when the query was neither invalidated nor fetched recently, otherwise one trailing
  // run. Measured from the end of the last fetch too, so that a query slower than its throttle is
  // not refetched back to back.
  throttled = (queries: LiveQuery[]) => {
    // Its 'removed' events drop the throttle state of collected queries.
    ensureCacheSubscription();
    const now = Date.now();
    const immediate = new Set<string>();
    queries.forEach((query) => {
      const hash = query.queryHash;
      const state = throttleOf(hash);
      if (state.timer !== undefined) return;
      const throttleMs = query.meta.live.throttleMs ?? DEFAULT_THROTTLE_MS;
      const since = Math.max(state.lastRun, query.state.dataUpdatedAt, query.state.errorUpdatedAt);
      const wait = since + throttleMs - now;
      if (wait <= 0) {
        state.lastRun = now;
        immediate.add(hash);
      } else {
        state.timer = setTimeout(() => {
          state.timer = undefined;
          state.lastRun = Date.now();
          invalidateNow(new Set([hash]));
        }, wait);
      }
    });
    if (immediate.size > 0) invalidateNow(immediate);
  };

  // In-flight queries keep the changes for their response; settled ones are matched now.
  const route = (changes: EntityChange[]) => {
    const index = indexChanges(changes);
    const matching: LiveQuery[] = [];
    liveQueries().forEach((query) => {
      if (isFetching(query)) {
        ensureCacheSubscription();
        park(query.queryHash, index);
      } else if (matches(query, index)) {
        matching.push(query);
      }
    });
    return matching;
  };

  const flush = () => {
    batchTimer = undefined;
    const changes = expand([...buffer.values()]);
    buffer.clear();
    if (changes.length === 0) return;
    const matching = route(changes);
    if (matching.length > 0) throttled(matching);
  };

  // The user's own change: the queries showing it are refetched now and the caller waits for them.
  // A fetch in flight is restarted (`cancelRefetch`, TanStack's default; the stream path avoids it so
  // that a steady flow of changes cannot keep restarting a slow fetch), except a first load, which
  // TanStack never cancels: that one is refetched once it settles.
  const applyNow = (changes: EntityChange[]) => {
    const index = indexChanges(expand(changes));
    const now = Date.now();
    const hashes = new Set<string>();
    ensureCacheSubscription();
    liveQueries().forEach((query) => {
      const hash = query.queryHash;
      if (!matches(query, index)) {
        // The response of a fetch in flight may show the changed entity: match it once it settles.
        if (isFetching(query)) park(hash, index);
        return;
      }
      if (isFetching(query) && query.state.data === undefined) {
        afterFetch.add(hash);
        return;
      }
      clearPending(hash);
      throttleOf(hash).lastRun = now;
      hashes.add(hash);
    });
    if (hashes.size === 0) return Promise.resolve();
    return queryClient.invalidateQueries({ predicate: query => hashes.has(query.queryHash) }, { cancelRefetch: true });
  };

  const applyChanges = (changes: EntityChange[], options?: ApplyOptions) => {
    if (changes.length === 0) return Promise.resolve();
    if (options?.immediate) return applyNow(changes);
    changes.forEach(change => buffer.set(changeKey(change), change));
    if (batchTimer === undefined) batchTimer = setTimeout(flush, BATCH_WINDOW_MS);
    return Promise.resolve();
  };

  const invalidateAllLive = () => throttled(liveQueries());

  const dropPending = () => {
    if (batchTimer !== undefined) clearTimeout(batchTimer);
    batchTimer = undefined;
    buffer.clear();
    throttles.forEach((state) => {
      if (state.timer !== undefined) clearTimeout(state.timer);
    });
    throttles.clear();
    afterFetch.clear();
    parked.clear();
  };

  // A fetch in flight was sent with the previous rights: cancel it first, since `cancelRefetch` does
  // not cancel a first load and its response would land as fresh data.
  const invalidateAll = async () => {
    dropPending();
    await queryClient.cancelQueries();
    await queryClient.invalidateQueries({ refetchType: focusManager.isFocused() ? 'active' : 'none' });
  };

  // TanStack's way to handle a loss of authorization: reset, not invalidate. The cached data was
  // read with the previous rights, and an inactive query would show it again on its next mount.
  const resetAll = () => {
    dropPending();
    return queryClient.resetQueries();
  };

  const dispose = () => {
    dropPending();
    unsubscribeCache?.();
    unsubscribeCache = undefined;
  };

  return {
    applyChanges,
    invalidateAllLive,
    invalidateAll,
    resetAll,
    dispose,
  };
};
