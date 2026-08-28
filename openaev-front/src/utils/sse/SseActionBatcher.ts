import { DATA_DELETE_BATCH_SUCCESS } from '../../constants/ActionTypes';

/** Normalizr output for a single SSE entity event. */
export interface NormalizedSsePayload {
  entities?: Record<string, Record<string, unknown>>;
  result?: unknown;
}

export interface ReduxAction {
  type: string;
  payload: unknown;
}

export interface DeletedEntity {
  id: string;
  type: string;
}

/**
 * Bound on distinct entities buffered between flushes. Under normal (visible)
 * conditions the queue is flushed every 200ms and never comes close to this;
 * the cap only trips after a long throttled background period, where replaying
 * the backlog is pointless - a single API resync is cheaper and more correct.
 */
export const MAX_PENDING_ENTITIES = 1000;

/**
 * Coalesces the SSE event backlog per entity so it stays bounded by the number
 * of DISTINCT entities touched (not by event volume), and drains it in a
 * constant number of Redux dispatches.
 *
 * SSE payloads are full entity snapshots, so last-write-wins coalescing is
 * lossless: a newer snapshot supersedes older snapshots of the same entity,
 * a delete supersedes pending snapshots, and a snapshot arriving after a
 * pending delete (entity re-created) supersedes the delete.
 */
export class SseActionBatcher {
  private upserts = new Map<string, {
    eventType: string;
    payload: NormalizedSsePayload;
  }>();

  private deletes = new Map<string, DeletedEntity>();

  get size(): number {
    return this.upserts.size + this.deletes.size;
  }

  addUpsert(eventType: string, schemaName: string, entityId: string, payload: NormalizedSsePayload): void {
    const key = `${schemaName}:${entityId}`;
    this.deletes.delete(key);
    this.upserts.set(key, {
      eventType,
      payload,
    });
  }

  addDelete(schemaName: string, entityId: string): void {
    const key = `${schemaName}:${entityId}`;
    this.upserts.delete(key);
    this.deletes.set(key, {
      id: entityId,
      type: schemaName,
    });
  }

  isOverCap(): boolean {
    return this.size > MAX_PENDING_ENTITIES;
  }

  clear(): void {
    this.upserts.clear();
    this.deletes.clear();
  }

  /**
   * Collapse the whole backlog into a constant number of actions: one
   * composite upsert action per SSE event type plus a single batched delete
   * action. Draining thousands of accumulated events therefore costs a couple
   * of reducer passes and subscriber notifications instead of thousands.
   */
  drain(): ReduxAction[] {
    const actions: ReduxAction[] = [];
    const entitiesByEventType = new Map<string, Record<string, Record<string, unknown>>>();
    this.upserts.forEach(({ eventType, payload }) => {
      const payloadEntities = payload.entities ?? {};
      // The `settings` schema has a dedicated early-return branch in the
      // referential reducer that ignores every other entity type present in
      // the same payload, so settings events must stay in their own action.
      if (payloadEntities.settings) {
        actions.push({
          type: eventType,
          payload,
        });
        return;
      }
      const merged = entitiesByEventType.get(eventType) ?? {};
      Object.entries(payloadEntities).forEach(([entityType, byId]) => {
        merged[entityType] = Object.assign(merged[entityType] ?? {}, byId);
      });
      entitiesByEventType.set(eventType, merged);
    });
    entitiesByEventType.forEach((entities, eventType) => {
      actions.push({
        type: eventType,
        payload: { entities },
      });
    });
    if (this.deletes.size > 0) {
      actions.push({
        type: DATA_DELETE_BATCH_SUCCESS,
        payload: [...this.deletes.values()],
      });
    }
    this.clear();
    return actions;
  }
}
