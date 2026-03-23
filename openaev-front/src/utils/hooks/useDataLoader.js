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

const flushPendingActions = () => {
  batchTimeoutId = undefined;
  if (pendingActions.length === 0) return;
  const actions = pendingActions;
  pendingActions = [];
  const dispatch = (action) => store.dispatch(action);
  if (typeof requestIdleCallback === 'function') {
    idleCallbackId = requestIdleCallback(() => {
      idleCallbackId = undefined;
      actions.forEach(dispatch);
    }, { timeout: IDLE_CALLBACK_TIMEOUT });
  } else {
    actions.forEach(dispatch);
  }
};

const scheduleBatchedDispatch = (action) => {
  pendingActions.push(action);
  if (!batchTimeoutId) {
    batchTimeoutId = setTimeout(flushPendingActions, SSE_BATCH_INTERVAL);
  }
};

const cancelPendingBatches = () => {
  if (batchTimeoutId) {
    clearTimeout(batchTimeoutId);
    batchTimeoutId = undefined;
  }
  if (idleCallbackId && typeof cancelIdleCallback === 'function') {
    cancelIdleCallback(idleCallbackId);
    idleCallbackId = undefined;
  }
  pendingActions = [];
};

const useDataLoader = (loader = () => {}, refetchArg = []) => {
  const sseConnect = () => {
    if (reconnectTimeoutId) {
      clearTimeout(reconnectTimeoutId);
      reconnectTimeoutId = undefined;
    }
    if (autoReConnectIntervalId) {
      clearInterval(autoReConnectIntervalId);
      autoReConnectIntervalId = undefined;
    }
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
    if (EventSource !== undefined && sseClient === undefined) {
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
        if (reconnectTimeoutId) {
          clearTimeout(reconnectTimeoutId);
          reconnectTimeoutId = undefined;
        }
        if (autoReConnectIntervalId) {
          clearInterval(autoReConnectIntervalId);
          autoReConnectIntervalId = undefined;
        }
        cancelPendingBatches();
        sseClient.close();
        sseClient = undefined;
      }
    };
  }, refetchArg);
};

export default useDataLoader;
