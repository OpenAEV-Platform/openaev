import { describe, expect, it } from 'vitest';

import { DATA_DELETE_BATCH_SUCCESS, DATA_FETCH_SUCCESS, DATA_UPDATE_SUCCESS } from '../../../constants/ActionTypes';
import { MAX_PENDING_ENTITIES, type NormalizedSsePayload, SseActionBatcher } from '../../../utils/sse/SseActionBatcher';

const payloadFor = (entityType: string, id: string, body: Record<string, unknown>): NormalizedSsePayload => ({
  entities: {
    [entityType]: {
      [id]: {
        [`${entityType.slice(0, -1)}_id`]: id,
        ...body,
      },
    },
  },
  result: id,
});

describe('SseActionBatcher', () => {
  it('coalesces successive snapshots of the same entity, keeping the latest', () => {
    const batcher = new SseActionBatcher();
    batcher.addUpsert(DATA_UPDATE_SUCCESS, 'injects', 'i1', payloadFor('injects', 'i1', { v: 1 }));
    batcher.addUpsert(DATA_UPDATE_SUCCESS, 'injects', 'i1', payloadFor('injects', 'i1', { v: 2 }));
    batcher.addUpsert(DATA_UPDATE_SUCCESS, 'injects', 'i1', payloadFor('injects', 'i1', { v: 3 }));
    expect(batcher.size).toBe(1);
    const actions = batcher.drain();
    expect(actions).toHaveLength(1);
    expect(actions[0].type).toBe(DATA_UPDATE_SUCCESS);
    const payload = actions[0].payload as { entities: Record<string, Record<string, { v: number }>> };
    expect(payload.entities.injects.i1.v).toBe(3);
  });

  it('lets a delete supersede pending snapshots of the same entity', () => {
    const batcher = new SseActionBatcher();
    batcher.addUpsert(DATA_UPDATE_SUCCESS, 'injects', 'i1', payloadFor('injects', 'i1', { v: 1 }));
    batcher.addDelete('injects', 'i1');
    expect(batcher.size).toBe(1);
    const actions = batcher.drain();
    expect(actions).toHaveLength(1);
    expect(actions[0].type).toBe(DATA_DELETE_BATCH_SUCCESS);
    expect(actions[0].payload).toEqual([{
      id: 'i1',
      type: 'injects',
    }]);
  });

  it('lets a snapshot arriving after a pending delete supersede the delete (entity re-created)', () => {
    const batcher = new SseActionBatcher();
    batcher.addDelete('injects', 'i1');
    batcher.addUpsert(DATA_UPDATE_SUCCESS, 'injects', 'i1', payloadFor('injects', 'i1', { v: 9 }));
    const actions = batcher.drain();
    expect(actions).toHaveLength(1);
    expect(actions[0].type).toBe(DATA_UPDATE_SUCCESS);
  });

  it('merges distinct entities into one composite action per event type', () => {
    const batcher = new SseActionBatcher();
    batcher.addUpsert(DATA_UPDATE_SUCCESS, 'injects', 'i1', payloadFor('injects', 'i1', { v: 1 }));
    batcher.addUpsert(DATA_UPDATE_SUCCESS, 'exercises', 'e1', payloadFor('exercises', 'e1', { v: 2 }));
    batcher.addUpsert(DATA_FETCH_SUCCESS, 'tags', 't1', payloadFor('tags', 't1', { v: 3 }));
    batcher.addDelete('logs', 'l1');
    batcher.addDelete('logs', 'l2');
    const actions = batcher.drain();
    // 1 composite per event type + 1 batched delete action.
    expect(actions).toHaveLength(3);
    const update = actions.find(a => a.type === DATA_UPDATE_SUCCESS);
    const fetch = actions.find(a => a.type === DATA_FETCH_SUCCESS);
    const deletes = actions.find(a => a.type === DATA_DELETE_BATCH_SUCCESS);
    const updatePayload = update?.payload as { entities: Record<string, Record<string, unknown>> };
    expect(Object.keys(updatePayload.entities).sort()).toEqual(['exercises', 'injects']);
    expect(fetch).toBeDefined();
    expect(deletes?.payload).toHaveLength(2);
  });

  it('keeps settings events in their own action (dedicated reducer branch)', () => {
    const batcher = new SseActionBatcher();
    batcher.addUpsert(DATA_UPDATE_SUCCESS, 'injects', 'i1', payloadFor('injects', 'i1', { v: 1 }));
    batcher.addUpsert(DATA_UPDATE_SUCCESS, 'settings', 's1', {
      entities: {
        settings: {
          s1: {
            setting_key: 'k',
            setting_value: 'v',
          },
        },
      },
      result: 's1',
    });
    const actions = batcher.drain();
    expect(actions).toHaveLength(2);
    const settingsAction = actions.find(a => (a.payload as NormalizedSsePayload).entities?.settings);
    expect(settingsAction).toBeDefined();
  });

  it('drains to empty and reports overflow only above the cap', () => {
    const batcher = new SseActionBatcher();
    for (let i = 0; i < MAX_PENDING_ENTITIES; i += 1) {
      batcher.addUpsert(DATA_UPDATE_SUCCESS, 'injects', `i${i}`, payloadFor('injects', `i${i}`, { v: i }));
    }
    expect(batcher.isOverCap()).toBe(false);
    batcher.addUpsert(DATA_UPDATE_SUCCESS, 'injects', 'one-more', payloadFor('injects', 'one-more', { v: 0 }));
    expect(batcher.isOverCap()).toBe(true);
    batcher.drain();
    expect(batcher.size).toBe(0);
    expect(batcher.isOverCap()).toBe(false);
  });

  it('does not count repeated events on the same entity against the cap', () => {
    const batcher = new SseActionBatcher();
    for (let i = 0; i < MAX_PENDING_ENTITIES * 3; i += 1) {
      batcher.addUpsert(DATA_UPDATE_SUCCESS, 'injects', 'busy', payloadFor('injects', 'busy', { v: i }));
    }
    expect(batcher.size).toBe(1);
    expect(batcher.isOverCap()).toBe(false);
  });

  it('clear() empties the backlog', () => {
    const batcher = new SseActionBatcher();
    batcher.addUpsert(DATA_UPDATE_SUCCESS, 'injects', 'i1', payloadFor('injects', 'i1', { v: 1 }));
    batcher.addDelete('logs', 'l1');
    batcher.clear();
    expect(batcher.size).toBe(0);
    expect(batcher.drain()).toEqual([]);
  });
});
