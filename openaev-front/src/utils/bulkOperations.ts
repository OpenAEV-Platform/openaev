import { useSyncExternalStore } from 'react';

import { simpleCall } from './Action';
import { type BulkOperation as GeneratedBulkOperation } from './api-types';

/**
 * Massive (bulk) operation snapshot streamed through the `bulk-operation` SSE events and served
 * by GET /api/bulk-operations. Derived from the generated API type so the frontend cannot drift
 * from the backend contract; the fields the store and the header indicator rely on are narrowed
 * to required, which the runtime guard in {@link ingestBulkOperation} enforces on ingestion.
 */
export type BulkOperation = GeneratedBulkOperation & Required<
  Pick<
    GeneratedBulkOperation,
    | 'bulk_operation_id'
    | 'bulk_operation_action'
    | 'bulk_operation_entity'
    | 'bulk_operation_total'
    | 'bulk_operation_processed'
    | 'bulk_operation_status'
    | 'bulk_operation_started_at'
  >
>;

// The generated type marks every field optional: narrow wire payloads (SSE events, seed endpoint)
// to the fields the store and the indicator actually dereference before ingesting them.
const isUsableBulkOperation = (operation: GeneratedBulkOperation): operation is BulkOperation =>
  typeof operation.bulk_operation_id === 'string'
  && typeof operation.bulk_operation_action === 'string'
  && typeof operation.bulk_operation_entity === 'string'
  && typeof operation.bulk_operation_total === 'number'
  && typeof operation.bulk_operation_processed === 'number'
  && typeof operation.bulk_operation_status === 'string'
  && typeof operation.bulk_operation_started_at === 'string';

// The indicator is permanent: finished operations stay listed as a per-user history (the
// backend journals the last operations per user), capped so the popover stays lightweight.
const HISTORY_SIZE = 100;

const operations = new Map<string, BulkOperation>();
const subscribers = new Set<() => void>();
// Cached immutable snapshot: useSyncExternalStore requires getSnapshot to return a stable
// reference between emits, otherwise it loops re-rendering.
let snapshot: BulkOperation[] = [];
// Monotonic counter of operations that reached a terminal state, so screens holding data
// outside the Redux entity store (e.g. the engine-backed dashboard widgets) can refresh once
// per finished operation.
let finishedCount = 0;

const isFinished = (operation: BulkOperation) => operation.bulk_operation_status !== 'RUNNING';

const rebuildSnapshot = () => {
  // Running operations first, then the history by recency, mirroring the backend ordering.
  const sorted = [...operations.values()].sort((a, b) => {
    if (isFinished(a) !== isFinished(b)) {
      return isFinished(a) ? 1 : -1;
    }
    return new Date(b.bulk_operation_started_at).getTime() - new Date(a.bulk_operation_started_at).getTime();
  });
  snapshot = sorted.slice(0, HISTORY_SIZE);
  sorted.slice(HISTORY_SIZE).forEach(operation => operations.delete(operation.bulk_operation_id));
  subscribers.forEach(notify => notify());
};

/**
 * Ingests a bulk operation snapshot (from an SSE event or the seed endpoint) into the store.
 * Returns true when this snapshot is the transition to a terminal state (completed or failed),
 * so the caller can refresh the data of mounted screens exactly once per operation.
 */
export const ingestBulkOperation = (operation: GeneratedBulkOperation): boolean => {
  if (!isUsableBulkOperation(operation)) {
    return false;
  }
  const previous = operations.get(operation.bulk_operation_id);
  // SSE events are delivered asynchronously per consumer: never let a stale progress snapshot
  // overwrite a terminal one.
  if (previous && isFinished(previous) && !isFinished(operation)) {
    return false;
  }
  operations.set(operation.bulk_operation_id, operation);
  const justFinished = isFinished(operation) && (!previous || !isFinished(previous));
  if (justFinished) {
    finishedCount += 1;
  }
  rebuildSnapshot();
  return justFinished;
};

/** Seeds the store from the backend registry (page load, SSE reconnect). */
export const seedBulkOperations = async () => {
  try {
    const result = await simpleCall('/api/bulk-operations', undefined, false);
    (result.data as GeneratedBulkOperation[]).forEach((operation) => {
      ingestBulkOperation(operation);
    });
  } catch {
    // Seeding is best-effort: live SSE events will populate the store anyway.
  }
};

const subscribe = (notify: () => void) => {
  subscribers.add(notify);
  return () => {
    subscribers.delete(notify);
  };
};

const getSnapshot = () => snapshot;

/** Reactive list of bulk operations (running first by recency), for the header indicator. */
export const useBulkOperations = (): BulkOperation[] => useSyncExternalStore(subscribe, getSnapshot);

const getFinishedCount = () => finishedCount;

/**
 * Reactive count of bulk operations that reached a terminal state since the app loaded. Screens
 * whose data lives outside the Redux entity store (engine-backed dashboards) watch it to refresh
 * once per finished massive operation.
 */
export const useBulkOperationsFinishedCount = (): number => useSyncExternalStore(subscribe, getFinishedCount);
