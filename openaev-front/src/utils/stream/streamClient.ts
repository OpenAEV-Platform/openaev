import { DATA_DELETE_SUCCESS, DATA_FETCH_SUCCESS, DATA_UPDATE_SUCCESS } from '../../constants/ActionTypes';
import { buildUri } from '../Action';
import { type BulkOperation as GeneratedBulkOperation } from '../api-types';
import { ingestBulkOperation } from '../bulkOperations';
import { evaluateWatchdogTick } from '../sse/watchdogPolicy';
import { type EntityChange, type EntityChangeAction, type EntityRef } from './entityChange';
import { STREAM_TYPES } from './streamTypes';

/** Raw `message` event payload as sent by the backend today. Only the Redux adapter may read `instance`. */
export interface StreamMessage {
  event_type: string;
  attribute_id: string;
  attribute_schema: string;
  instance: Record<string, unknown>;
  listened: boolean;
}

export interface ConnectionOpenInfo {
  /** True for the first successful open of this stream session, false for every reconnection. */
  first: boolean;
}

const STREAM_PATH = '/api/stream';

const EVENT_TRY_DELAY = 1500;
const EVENT_PING_MAX_TIME = 5000;

const ERROR_30S_MAX_TIME = 30000;
const ERROR_5M_MAX_TIME = 300000;
const ERROR_2S_DELAY = 2000;
const ERROR_10S_DELAY = 10000;
const ERROR_30S_DELAY = 30000;

// Session state: reset by stopStream so that a later start behaves like a first start.
let started = false;
let hasOpened = false;
let source: EventSource | undefined;
let lastPingDate = new Date().getTime();
// Set from the moment the connection is seen broken until the next open.
let down = false;
// When the current outage began, kept across reconnection attempts: the backoff grows with the
// outage, which a per-connection clock would always see as just started. A device sleep is not an
// outage: the clock only starts once the connection is seen broken.
let downSince = 0;
let lastWatchdogTickAt = new Date().getTime();
let reconnectTimeoutId: ReturnType<typeof setTimeout> | undefined;
let watchdogIntervalId: ReturnType<typeof setInterval> | undefined;

// Subscriptions live at module level, outside the session state, so they survive stop/start
// and every reconnection.
const messageHandlers = new Set<(message: StreamMessage) => void>();
const entityChangeHandlers = new Set<(change: EntityChange) => void>();
const bulkOperationEndHandlers = new Set<(entity: string | undefined) => void>();
const connectionOpenHandlers = new Set<(info: ConnectionOpenInfo) => void>();
const connectionLostHandlers = new Set<() => void>();

// Feature-scoped subscribers to named stream event types, keyed by type.
const streamEventSubscribers = new Map<string, Set<(event: MessageEvent) => void>>();
// One stable listener per type, so a type is attached at most once per connection (attaching per
// subscriber would deliver an event twice to a type that already had one). It reads the subscriber
// set at event time rather than closing over it, so subscribing and unsubscribing mid-connection
// take effect immediately.
const streamEventDispatchers = new Map<string, (event: MessageEvent) => void>();

const isDocumentHidden = () => typeof document !== 'undefined' && document.visibilityState === 'hidden';

// A handler must never break the SSE processing of this tab, nor the handlers after it.
const reportHandlerError = (error: unknown) => {
  if (import.meta.env.DEV && import.meta.env.MODE !== 'test') {
    // eslint-disable-next-line no-console
    console.error('[stream] A stream handler failed', error);
  }
};

const emit = <T>(handlers: Set<(payload: T) => void>, payload: T) => {
  [...handlers].forEach((handler) => {
    try {
      handler(payload);
    } catch (error) {
      // Isolated on purpose, but surfaced while developing.
      reportHandlerError(error);
    }
  });
};

const subscribeTo = <T>(handlers: Set<(payload: T) => void>, handler: (payload: T) => void) => {
  handlers.add(handler);
  return () => {
    handlers.delete(handler);
  };
};

const dispatcherFor = (type: string) => {
  let dispatcher = streamEventDispatchers.get(type);
  if (dispatcher === undefined) {
    dispatcher = (event: MessageEvent) => {
      const handlers = streamEventSubscribers.get(type);
      if (handlers === undefined) return;
      [...handlers].forEach((handler) => {
        try {
          handler(event);
        } catch (error) {
          // A subscriber must never break the SSE processing of this tab.
          reportHandlerError(error);
        }
      });
    };
    streamEventDispatchers.set(type, dispatcher);
  }
  return dispatcher;
};

// Parent a change also affects, keyed by stream schema. The server already checks permissions on
// that parent (an agent is checked on its asset), so a consumer may refetch by it.
const ROOT_OF: Record<string, (instance: Record<string, unknown>) => EntityRef | undefined> = {
  [STREAM_TYPES.AGENTS]: (instance) => {
    const assetId = instance.agent_asset;
    return typeof assetId === 'string' && assetId !== ''
      ? {
          type: STREAM_TYPES.ASSETS,
          id: assetId,
        }
      : undefined;
  },
};

// Own keys only: a schema named like an Object.prototype member must not resolve to it.
const rootOf = (schema: string, instance: Record<string, unknown>) =>
  (Object.hasOwn(ROOT_OF, schema) ? ROOT_OF[schema](instance) : undefined);

const actionOf = (eventType: string): EntityChangeAction => {
  switch (eventType) {
    case DATA_FETCH_SUCCESS:
      return 'create';
    case DATA_DELETE_SUCCESS:
      return 'delete';
    case DATA_UPDATE_SUCCESS:
    default:
      return 'update';
  }
};

// The instance is read here and nowhere past the stream adapter: consumers only get type, id,
// action and root.
const toEntityChange = (message: StreamMessage): EntityChange | undefined => {
  const rawId = message.instance[message.attribute_id];
  if (rawId === undefined || rawId === null || rawId === '') return undefined;
  const change: EntityChange = {
    type: message.attribute_schema,
    id: String(rawId),
    action: actionOf(message.event_type),
  };
  const root = rootOf(message.attribute_schema, message.instance);
  if (root !== undefined) {
    change.root = root;
  }
  return change;
};

const handleMessage = (event: MessageEvent) => {
  let data: StreamMessage;
  try {
    data = JSON.parse(event.data);
  } catch {
    return;
  }
  if (!data?.listened) return;
  // A malformed event (missing schema/id attribute - e.g. a backend entity whose @Id lives deeper
  // in its class hierarchy than the event builder inspects) must never throw out of the SSE
  // handler: it would flood the console with uncaught errors, one per mutation, while a running
  // simulation streams. Skip it - the initial fetch and later reloads reconcile.
  if (!data.attribute_schema || !data.attribute_id || typeof data.instance !== 'object' || data.instance === null) {
    // eslint-disable-next-line no-console
    console.warn('[SSE] Skipping malformed stream event (missing schema or id attribute)', {
      attribute_schema: data?.attribute_schema,
      event_type: data?.event_type,
    });
    return;
  }
  emit(messageHandlers, data);
  let change: EntityChange | undefined;
  try {
    change = toEntityChange(data);
  } catch (error) {
    reportHandlerError(error);
    return;
  }
  if (change !== undefined) {
    emit(entityChangeHandlers, change);
  }
};

// Massive operations do not stream one event per mutated entity (which used to force a refresh per
// delete on every open screen): they emit aggregated progress snapshots instead. The header
// indicator renders them live, and consumers refresh exactly once, when an operation reaches a
// terminal state.
const handleBulkOperation = (event: MessageEvent) => {
  // A malformed payload must never break the SSE processing of this tab: skip it, the periodic
  // seed endpoint reconciles the indicator anyway.
  let operation: GeneratedBulkOperation;
  let justFinished: boolean;
  try {
    operation = JSON.parse(event.data);
    justFinished = ingestBulkOperation(operation);
  } catch {
    return;
  }
  if (justFinished) {
    emit(bulkOperationEndHandlers, operation.bulk_operation_entity);
  }
};

const clearTimers = () => {
  if (reconnectTimeoutId !== undefined) {
    clearTimeout(reconnectTimeoutId);
    reconnectTimeoutId = undefined;
  }
  if (watchdogIntervalId !== undefined) {
    clearInterval(watchdogIntervalId);
    watchdogIntervalId = undefined;
  }
};

// The delay grows with the outage, with ±30% jitter so that the tabs of every user do not all
// reconnect, and reload, in the same instant when the platform comes back.
const reconnectDelay = () => {
  const outage = new Date().getTime() - downSince;
  let delay = ERROR_30S_DELAY;
  if (outage < ERROR_30S_MAX_TIME) {
    delay = ERROR_2S_DELAY;
  } else if (outage < ERROR_5M_MAX_TIME) {
    delay = ERROR_10S_DELAY;
  }
  return Math.round(delay * (0.7 + Math.random() * 0.6));
};

const scheduleReconnect = () => {
  // eslint-disable-next-line @typescript-eslint/no-use-before-define -- connect and its reconnection call each other
  reconnectTimeoutId = setTimeout(connect, reconnectDelay());
};

// Emitted once per outage, so consumers can fall back to polling right away.
const markDown = () => {
  if (down) return;
  down = true;
  downSince = new Date().getTime();
  emit(connectionLostHandlers, undefined);
};

const connect = (): void => {
  // Bail out where EventSource is unavailable (jsdom/tests, older browsers) so reconnect timers
  // can never throw on `new EventSource(...)`.
  if (!started || typeof EventSource === 'undefined') return;
  clearTimers();
  // Reset the ping clock for the fresh connection. Otherwise a stale lastPingDate would make the
  // watchdog fire immediately and loop before the first ping of the new connection arrives.
  lastPingDate = new Date().getTime();
  lastWatchdogTickAt = new Date().getTime();
  const client = new EventSource(buildUri(STREAM_PATH), { withCredentials: true });
  source = client;
  watchdogIntervalId = setInterval(() => {
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
      // A silent connection (hung backend, buffering proxy) backs off like a failing one.
      clearTimers();
      client.close();
      markDown();
      scheduleReconnect();
    }
  }, EVENT_TRY_DELAY);
  client.addEventListener('open', () => {
    const first = !hasOpened;
    hasOpened = true;
    down = false;
    emit(connectionOpenHandlers, { first });
  });
  client.addEventListener('message', handleMessage);
  client.addEventListener('bulk-operation', handleBulkOperation);
  // Feature-scoped stream events (see subscribeStreamEvent): one dispatcher per type, re-attached
  // to every new connection, so a subscriber never has to know the connection was recycled.
  streamEventSubscribers.forEach((_handlers, type) => {
    client.addEventListener(type, dispatcherFor(type));
  });
  client.addEventListener('ping', () => {
    lastPingDate = new Date().getTime();
  });
  client.onerror = () => {
    if (watchdogIntervalId !== undefined) {
      clearInterval(watchdogIntervalId);
      watchdogIntervalId = undefined;
    }
    client.close();
    markDown();
    scheduleReconnect();
  };
};

// Browsers throttle timers in hidden tabs, so the ping clock lags behind a perfectly healthy
// connection: reset it on refocus so the watchdog never mistakes the background period for a dead
// connection (which would close/reopen the EventSource and refetch every consumer on every tab
// switch).
const handleVisibilityChange = () => {
  if (isDocumentHidden() || !started) return;
  lastPingDate = new Date().getTime();
};

if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', handleVisibilityChange);
}

/** Opens the shared stream for the current tenant. Idempotent. */
export const startStream = (): void => {
  if (started) return;
  // Without EventSource there is no stream to start: consumers then load on their own (see useDataLoader).
  if (typeof EventSource === 'undefined') return;
  started = true;
  down = false;
  connect();
};

/** Closes the shared stream (logout, tenant switch). Idempotent. */
export const stopStream = (): void => {
  started = false;
  hasOpened = false;
  down = false;
  clearTimers();
  if (source !== undefined) {
    source.close();
    source = undefined;
  }
};

/** Every entity change, already reduced to its type/id/action/root. */
export const onEntityChange = (handler: (change: EntityChange) => void): (() => void) =>
  subscribeTo(entityChangeHandlers, handler);

/** Raw `message` payloads, for the legacy Redux adapter only. */
export const onStreamMessage = (handler: (message: StreamMessage) => void): (() => void) =>
  subscribeTo(messageHandlers, handler);

/** A bulk operation reached a terminal state; `entity` is its `bulk_operation_entity` when known. */
export const onBulkOperationEnd = (handler: (entity: string | undefined) => void): (() => void) =>
  subscribeTo(bulkOperationEndHandlers, handler);

/** The connection (re)opened. Anything received before may have been missed on a reconnection. */
export const onConnectionOpen = (handler: (info: ConnectionOpenInfo) => void): (() => void) =>
  subscribeTo(connectionOpenHandlers, handler);

/** The connection broke (error or silent watchdog): emitted once per outage, until the next open. */
export const onConnectionLost = (handler: () => void): (() => void) =>
  subscribeTo(connectionLostHandlers, handler);

/** Whether a session started the stream (and did not stop it). */
export const isStreamStarted = (): boolean => started;

/** Whether the stream opened at least once since the last `startStream`. */
export const hasStreamOpened = (): boolean => hasOpened;

/**
 * Named, feature-scoped stream events (e.g. 'attack-path-version').
 *
 * A feature that only needs a "something changed, refetch" nudge uses this instead of opening its
 * own connection: the handler is attached to the current EventSource and re-attached to every later
 * one, so reconnections are invisible to the caller. Handlers are per-type and isolated - one that
 * throws never breaks the tab's SSE processing.
 */
export const subscribeStreamEvent = (type: string, handler: (event: MessageEvent) => void): (() => void) => {
  const handlers = streamEventSubscribers.get(type) ?? new Set();
  const isFirstOfType = handlers.size === 0;
  handlers.add(handler);
  streamEventSubscribers.set(type, handlers);
  if (source !== undefined && isFirstOfType) {
    // Live connection, first subscriber of this type: attach the dispatcher once. Later subscribers
    // ride the same one, and the reconnect path attaches it to every subsequent connection.
    source.addEventListener(type, dispatcherFor(type));
  }
  return () => {
    handlers.delete(handler);
    // The dispatcher stays attached: it is a no-op with no subscribers, and keeping it means the next
    // subscriber of this type is reached on the current connection without re-attaching.
  };
};

/**
 * Whether the stream is currently delivering: the server pings every second, so a ping older than
 * the watchdog threshold means the connection is effectively down. Callers that treat the stream as
 * a nudge use this to decide how hard they must poll as a safety net; an environment with no
 * EventSource at all is never healthy.
 */
export const isStreamHealthy = (): boolean => {
  if (typeof EventSource === 'undefined' || source === undefined || down) {
    return false;
  }
  return new Date().getTime() - lastPingDate < EVENT_PING_MAX_TIME;
};
