import { normalize, schema } from 'normalizr';
import { useEffect } from 'react';

import { DATA_DELETE_SUCCESS } from '../../constants/ActionTypes';
import { store } from '../../store';
import { SseActionBatcher } from '../sse/SseActionBatcher';
import {
  hasStreamOpened,
  isStreamStarted,
  onBulkOperationEnd,
  onConnectionOpen,
  onStreamMessage,
} from '../stream/streamClient';

export { isStreamHealthy, subscribeStreamEvent } from '../stream/streamClient';

const listeners = new Map();

const SSE_BATCH_INTERVAL = 200;
const IDLE_CALLBACK_TIMEOUT = 1000;
// Coalesced per-entity backlog of SSE events awaiting dispatch. Bounded by the
// number of distinct entities touched (last snapshot wins), never by raw event
// volume - critical for background tabs where flush timers are throttled but
// SSE network events keep arriving for many minutes.
const batcher = new SseActionBatcher();
// Set when the backlog overflowed (typically during a long background period):
// the buffered events were dropped and a full reload of every registered
// loader replaces them as soon as the tab is visible again.
let needsResync = false;
let batchTimeoutId;
let idleCallbackId;

const drainPendingActions = () => {
  batcher.drain().forEach(action => store.dispatch(action));
};

const reloadAllListeners = () => {
  [...listeners.keys()].forEach(load => load());
};

const flushPendingActions = () => {
  batchTimeoutId = undefined;
  if (batcher.size === 0) return;
  if (typeof requestIdleCallback !== 'function') {
    drainPendingActions();
    return;
  }
  // Keep a single idle callback in flight at a time. It drains whatever is
  // queued when it actually runs, so later 200ms batches never schedule extra
  // idle callbacks that we cannot track/cancel (which could otherwise dispatch
  // after the last listener unmounts).
  if (idleCallbackId !== undefined) return;
  idleCallbackId = requestIdleCallback(() => {
    idleCallbackId = undefined;
    drainPendingActions();
  }, { timeout: IDLE_CALLBACK_TIMEOUT });
};

const scheduleBatchedDispatch = () => {
  // Timer handles are compared against undefined (not truthiness) since a valid
  // handle can be 0 in some implementations/polyfills.
  if (batchTimeoutId === undefined) {
    batchTimeoutId = setTimeout(flushPendingActions, SSE_BATCH_INTERVAL);
  }
};

const cancelScheduledFlush = () => {
  if (batchTimeoutId !== undefined) {
    clearTimeout(batchTimeoutId);
    batchTimeoutId = undefined;
  }
  // requestIdleCallback can return 0 in some polyfills, so compare against
  // undefined explicitly (matching flushPendingActions) rather than a truthy
  // check that would skip cancellation for a 0 handle.
  if (idleCallbackId !== undefined && typeof cancelIdleCallback === 'function') {
    cancelIdleCallback(idleCallbackId);
    idleCallbackId = undefined;
  }
};

const cancelPendingBatches = () => {
  cancelScheduledFlush();
  batcher.clear();
};

// Drop the (now stale) backlog and refetch everything the mounted components
// need. Used when the coalesced backlog overflowed its cap: replaying that
// much history through the store is slower than a fresh API load.
const resyncNow = () => {
  needsResync = false;
  cancelPendingBatches();
  reloadAllListeners();
};

const isDocumentHidden = () => typeof document !== 'undefined' && document.visibilityState === 'hidden';

// Flush (or resync) as soon as the tab becomes visible again: the throttled
// timers may not have drained the backlog for many minutes, and doing it right
// on the visibility transition keeps the refocused page snappy.
const handleVisibilityChange = () => {
  if (isDocumentHidden()) return;
  if (listeners.size === 0) return;
  if (needsResync) {
    resyncNow();
  } else if (batcher.size > 0) {
    cancelScheduledFlush();
    drainPendingActions();
  }
};

if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', handleVisibilityChange);
}

// The stream itself lives in streamClient (session lifecycle); this is its Redux consumer. Events
// are only applied while a loader is mounted: with none, the store has nobody to feed and the next
// mounted loader fetches fresh data anyway.
onConnectionOpen(({ first }) => {
  // The full reload below supersedes anything still buffered (or dropped)
  // from the previous connection.
  cancelPendingBatches();
  if (!first && isDocumentHidden()) {
    // A reconnection in a background tab reloads when the tab is shown again, so that every tab of
    // every user does not reload at once when the platform comes back. The first open always
    // loads: the loaders have no data yet.
    needsResync = true;
    return;
  }
  needsResync = false;
  reloadAllListeners();
});

onStreamMessage((data) => {
  // Once a full resync is pending (the backlog overflowed while the tab
  // was hidden), individual events are stale: skip them.
  if (needsResync || listeners.size === 0) return;
  const entityId = data.instance[data.attribute_id];
  if (data.event_type === DATA_DELETE_SUCCESS) {
    batcher.addDelete(data.attribute_schema, entityId);
  } else {
    const schemaInfo = { idAttribute: data.attribute_id };
    const schemas = new schema.Entity(
      data.attribute_schema,
      {},
      schemaInfo,
    );
    const dataNormalize = normalize(data.instance, schemas);
    batcher.addUpsert(data.event_type, data.attribute_schema, entityId, dataNormalize);
  }
  if (batcher.isOverCap()) {
    cancelPendingBatches();
    needsResync = true;
    if (!isDocumentHidden()) {
      // Overflow while visible (extreme burst): resync immediately.
      // While hidden, the resync waits for the visibility transition so
      // a background tab never hammers the API.
      resyncNow();
    }
    return;
  }
  scheduleBatchedDispatch();
});

// The data of mounted screens is refreshed exactly once, when a massive operation reaches a
// terminal state.
onBulkOperationEnd(() => {
  if (listeners.size === 0) return;
  if (isDocumentHidden()) {
    // Hidden tab: defer the reload to the visibility transition, like the
    // backlog-overflow resync, so background tabs never hammer the API.
    needsResync = true;
  } else {
    resyncNow();
  }
});

const useDataLoader = (loader = () => {}, refetchArg = []) => {
  useEffect(() => {
    listeners.set(loader, '');
    // Pristine (the session started the stream, it never opened): the first open reloads every
    // registered loader, so loading here too would double the requests at the launch of the app.
    // Outside a session (no stream started) nothing will reload it: load now.
    if (hasStreamOpened() || !isStreamStarted()) {
      const load = async () => {
        await loader();
      };
      load();
    }
    return () => {
      listeners.delete(loader);
      if (listeners.size === 0) {
        cancelPendingBatches();
        needsResync = false;
      }
    };
  }, refetchArg);
};

export default useDataLoader;
