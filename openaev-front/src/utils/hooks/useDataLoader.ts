import { normalize, schema } from 'normalizr';
import { useEffect } from 'react';

import { DATA_DELETE_SUCCESS } from '../../constants/ActionTypes';
import { store } from '../../store';
import { buildUri } from '../Action';

const EVENT_TRY_DELAY = 1500;
const EVENT_PING_MAX_TIME = 5000;

const ERROR_30S_MAX_TIME = 30000;
const ERROR_5M_MAX_TIME = 300000;
const ERROR_5S_DELAY = 5000;
const ERROR_30S_DELAY = 30000;
const ERROR_2M_DELAY = 120000;

// pristine is used to avoid duplicate requests at the launch of the app
let pristine = true;
let sseClient: EventSource | undefined;
let lastPingDate = new Date().getTime();
const listeners = new Map();
const useDataLoader = (loader = () => {}, refetchArg: unknown[] = []) => {
  const sseConnect = () => {
    sseClient = new EventSource(buildUri('/api/stream'), { withCredentials: true });
    const autoReConnect = setInterval(() => {
      const current = new Date().getTime();
      if (current - lastPingDate > EVENT_PING_MAX_TIME) {
        clearInterval(autoReConnect);
        if (sseClient) {
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
          const deleteEvent = {
            type: DATA_DELETE_SUCCESS,
            payload,
            fromStream: true,
          };
          store.dispatch(deleteEvent);
        } else {
          const schemaInfo = { idAttribute: data.attribute_id };
          const schemas = new schema.Entity(
            data.attribute_schema,
            {},
            schemaInfo,
          );
          const dataNormalize = normalize(data.instance, schemas);
          const storeEvent = {
            type: data.event_type,
            payload: dataNormalize,
            fromStream: true,
          };
          store.dispatch(storeEvent);
        }
      }
    });
    sseClient.addEventListener('ping', () => {
      lastPingDate = new Date().getTime();
    });
    sseClient.onerror = () => {
      clearInterval(autoReConnect);
      if (sseClient != null) {
        sseClient.close();
      }
      const timeFromLastPingDate = new Date().getTime() - lastPingDate;
      if (timeFromLastPingDate < ERROR_30S_MAX_TIME) {
        setTimeout(sseConnect, ERROR_5S_DELAY);// Before 30s time to retry is 5s
      } else if (timeFromLastPingDate < ERROR_5M_MAX_TIME) {
        setTimeout(sseConnect, ERROR_30S_DELAY); // Before 5 min time to retry is 30s
      } else {
        setTimeout(sseConnect, ERROR_2M_DELAY);// After 5 min time to retry is 2m
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
      // Remove the listener
      listeners.delete(loader);
      // If its the last one, disconnect the stream
      if (listeners.size === 0 && sseClient) {
        sseClient.close();
        sseClient = undefined;
      }
    };
  }, refetchArg);
};

export default useDataLoader;
