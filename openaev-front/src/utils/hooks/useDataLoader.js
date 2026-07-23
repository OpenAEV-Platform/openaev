import { normalize, schema } from 'normalizr';
import { useEffect } from 'react';

import { DATA_DELETE_SUCCESS } from '../../constants/ActionTypes';
import { store } from '../../store';
import { buildUri } from '../Action';
import { ingestBulkOperation } from '../bulkOperations';
import { SseActionBatcher } from '../sse/SseActionBatcher';
import { evaluateWatchdogTick } from '../sse/watchdogPolicy';

const EVENT_TRY_DELAY = 1500;
const EVENT_PING_MAX_TIME = 5000;

const ERROR_30S_MAX_TIME = 30000;
const ERROR_5M_MAX_TIME = 300000;
const ERROR_2S_DELAY = 2000;
const ERROR_10S_DELAY = 10000;
const ERROR_30S_DELAY = 30000;

// pristine is used to avoid duplicate requests at the launch of the app
let pristine = true;
let sseClient;
let lastPingDate = new Date().getTime();
let lastWatchdogTickAt = new Date().getTime();
let reconnectTimeoutId;
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
let autoReConnectIntervalId;

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
// on the visibility transition keeps the refocused page snappy. Also reset the
// ping clock so the watchdog never mistakes the background period for a dead
// connection (which would close/reopen the EventSource and refetch every
// mounted loader on every tab switch).
const handleVisibilityChange = () => {
  if (isDocumentHidden()) return;
  if (listeners.size === 0) return;
  lastPingDate = new Date().getTime();
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

const useDataLoader = (loader = () => {}, refetchArg = []) => {
  const sseConnect = () => {
    // Bail out where EventSource is unavailable (jsdom/tests, older browsers)
    // so reconnect timers/intervals can never throw on `new EventSource(...)`.
    if (typeof EventSource === 'undefined') {
      return undefined;
    }
    if (reconnectTimeoutId !== undefined) {
      clearTimeout(reconnectTimeoutId);
      reconnectTimeoutId = undefined;
    }
    if (autoReConnectIntervalId !== undefined) {
      clearInterval(autoReConnectIntervalId);
      autoReConnectIntervalId = undefined;
    }
    // Reset the ping clock for the fresh connection. Otherwise a stale
    // lastPingDate (e.g. after all listeners unmounted for a while and later
    // remounted) would make the autoReConnect check fire immediately and loop
    // before the first ping of the new connection arrives.
    lastPingDate = new Date().getTime();
    lastWatchdogTickAt = new Date().getTime();
    sseClient = new EventSource(buildUri('/api/stream'), { withCredentials: true });
    autoReConnectIntervalId = setInterval(() => {
      if (listeners.size === 0) {
        clearInterval(autoReConnectIntervalId);
        autoReConnectIntervalId = undefined;
        return;
      }
      const current = new Date().getTime();
      const decision = evaluateWatchdogTick({
        now: current,
        lastPingDate,
        lastTickAt: lastWatchdogTickAt,
        hidden: isDocumentHidden(),
        tickInterval: EVENT_TRY_DELAY,
        pingMaxTime: EVENT_PING_MAX_TIME,
      });
      lastWatchdogTickAt = current;
      if (decision === 'reset-ping-clock') {
        lastPingDate = current;
      } else if (decision === 'reconnect') {
        clearInterval(autoReConnectIntervalId);
        autoReConnectIntervalId = undefined;
        if (sseClient != null) {
          sseClient.close();
        }
        sseConnect();
      }
    }, EVENT_TRY_DELAY);
    sseClient.addEventListener('open', () => {
      pristine = false;
      // The full reload below supersedes anything still buffered (or dropped)
      // from the previous connection.
      needsResync = false;
      cancelPendingBatches();
      reloadAllListeners();
    });
    sseClient.addEventListener('message', (event) => {
      // Once a full resync is pending (the backlog overflowed while the tab
      // was hidden), individual events are stale: skip even the parse work.
      if (needsResync) return;
      const data = JSON.parse(event.data);
      if (data.listened) {
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
      }
    });
    // Massive operations do not stream one event per mutated entity (which used to force a
    // refresh per delete on every open screen): they emit aggregated progress snapshots instead.
    // The header indicator renders them live, and the data of mounted screens is refreshed
    // exactly once, when an operation reaches a terminal state.
    sseClient.addEventListener('bulk-operation', (event) => {
      const operation = JSON.parse(event.data);
      const justFinished = ingestBulkOperation(operation);
      if (justFinished) {
        if (isDocumentHidden()) {
          // Hidden tab: defer the reload to the visibility transition, like the
          // backlog-overflow resync, so background tabs never hammer the API.
          needsResync = true;
        } else {
          resyncNow();
        }
      }
    });
    sseClient.addEventListener('ping', () => {
      lastPingDate = new Date().getTime();
    });
    sseClient.onerror = () => {
      clearInterval(autoReConnectIntervalId);
      autoReConnectIntervalId = undefined;
      if (sseClient != null) {
        sseClient.close();
      }
      const timeFromLastPingDate = new Date().getTime() - lastPingDate;
      if (timeFromLastPingDate < ERROR_30S_MAX_TIME) {
        reconnectTimeoutId = setTimeout(sseConnect, ERROR_2S_DELAY);
      } else if (timeFromLastPingDate < ERROR_5M_MAX_TIME) {
        reconnectTimeoutId = setTimeout(sseConnect, ERROR_10S_DELAY);
      } else {
        reconnectTimeoutId = setTimeout(sseConnect, ERROR_30S_DELAY);
      }
    };
    return sseClient;
  };
  useEffect(() => {
    listeners.set(loader, '');
    if (typeof EventSource !== 'undefined' && sseClient === undefined) {
      sseClient = sseConnect();
    } else if (!pristine) {
      const load = async () => {
        await loader();
      };
      load();
    }
    return () => {
      listeners.delete(loader);
      if (listeners.size === 0) {
        if (reconnectTimeoutId !== undefined) {
          clearTimeout(reconnectTimeoutId);
          reconnectTimeoutId = undefined;
        }
        if (autoReConnectIntervalId !== undefined) {
          clearInterval(autoReConnectIntervalId);
          autoReConnectIntervalId = undefined;
        }
        cancelPendingBatches();
        needsResync = false;
        if (sseClient != null) {
          sseClient.close();
        }
        sseClient = undefined;
      }
    };
  }, refetchArg);
};

export default useDataLoader;
