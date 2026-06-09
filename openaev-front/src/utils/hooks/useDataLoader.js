import { normalize, schema } from 'normalizr';
import { useEffect } from 'react';

import { DATA_DELETE_SUCCESS } from '../../constants/ActionTypes';
import { store } from '../../store';
import { buildUri } from '../Action';

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
let reconnectTimeoutId;
const listeners = new Map();

const SSE_BATCH_INTERVAL = 200;
const IDLE_CALLBACK_TIMEOUT = 1000;
let pendingActions = [];
let batchTimeoutId;
let idleCallbackId;
let autoReConnectIntervalId;

const drainPendingActions = () => {
  const actions = pendingActions;
  pendingActions = [];
  actions.forEach(action => store.dispatch(action));
};

const flushPendingActions = () => {
  batchTimeoutId = undefined;
  if (pendingActions.length === 0) return;
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

const scheduleBatchedDispatch = (action) => {
  pendingActions.push(action);
  // Timer handles are compared against undefined (not truthiness) since a valid
  // handle can be 0 in some implementations/polyfills.
  if (batchTimeoutId === undefined) {
    batchTimeoutId = setTimeout(flushPendingActions, SSE_BATCH_INTERVAL);
  }
};

const cancelPendingBatches = () => {
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
  pendingActions = [];
};

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
    sseClient = new EventSource(buildUri('/api/stream'), { withCredentials: true });
    autoReConnectIntervalId = setInterval(() => {
      if (listeners.size === 0) {
        clearInterval(autoReConnectIntervalId);
        autoReConnectIntervalId = undefined;
        return;
      }
      const current = new Date().getTime();
      if (current - lastPingDate > EVENT_PING_MAX_TIME) {
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
      [...listeners.keys()].forEach(load => load());
    });
    sseClient.addEventListener('message', (event) => {
      const data = JSON.parse(event.data);
      if (data.listened) {
        if (data.event_type === DATA_DELETE_SUCCESS) {
          const payload = {
            id: data.instance[data.attribute_id],
            type: data.attribute_schema,
          };
          scheduleBatchedDispatch({
            type: DATA_DELETE_SUCCESS,
            payload,
          });
        } else {
          const schemaInfo = { idAttribute: data.attribute_id };
          const schemas = new schema.Entity(
            data.attribute_schema,
            {},
            schemaInfo,
          );
          const dataNormalize = normalize(data.instance, schemas);
          scheduleBatchedDispatch({
            type: data.event_type,
            payload: dataNormalize,
          });
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
        if (sseClient != null) {
          sseClient.close();
        }
        sseClient = undefined;
      }
    };
  }, refetchArg);
};

export default useDataLoader;
