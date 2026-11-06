import { beforeEach, describe, expect, it, vi } from 'vitest';

import { type EntityChange } from '../../../utils/query/entityChange';
import { type LiveRouter } from '../../../utils/query/liveRouter';
import connectStreamToLiveRouter from '../../../utils/stream/connectStreamToLiveRouter';
import { type ConnectionOpenInfo } from '../../../utils/stream/streamClient';

const stream = vi.hoisted(() => ({
  entityHandlers: new Set<(change: EntityChange) => void>(),
  bulkHandlers: new Set<(entity: string | undefined) => void>(),
  openHandlers: new Set<(info: ConnectionOpenInfo) => void>(),
}));

vi.mock('../../../utils/stream/streamClient', () => {
  const subscribe = <T>(handlers: Set<T>) => (handler: T) => {
    handlers.add(handler);
    return () => {
      handlers.delete(handler);
    };
  };
  return {
    onEntityChange: subscribe(stream.entityHandlers),
    onBulkOperationEnd: subscribe(stream.bulkHandlers),
    onConnectionOpen: subscribe(stream.openHandlers),
  };
});

const createRouter = () => ({
  applyChanges: vi.fn(() => Promise.resolve()),
  invalidateAllLive: vi.fn(),
  invalidateAll: vi.fn(() => Promise.resolve()),
  resetAll: vi.fn(() => Promise.resolve()),
  dispose: vi.fn(),
}) satisfies LiveRouter;

const emitChange = (change: EntityChange) => stream.entityHandlers.forEach(handler => handler(change));
const emitBulkEnd = (entity: string | undefined) => stream.bulkHandlers.forEach(handler => handler(entity));
const emitOpen = (first: boolean) => stream.openHandlers.forEach(handler => handler({ first }));

describe('connectStreamToLiveRouter', () => {
  let router: ReturnType<typeof createRouter>;

  beforeEach(() => {
    stream.entityHandlers.clear();
    stream.bulkHandlers.clear();
    stream.openHandlers.clear();
    router = createRouter();
  });

  it('given_anEntityChange_should_applyItBatchedNotImmediately', () => {
    // Arrange
    connectStreamToLiveRouter(router);
    const change: EntityChange = {
      type: 'agents',
      id: 'agent-1',
      action: 'update',
      parents: [{
        type: 'assets',
        id: 'asset-1',
      }],
    };

    // Act
    emitChange(change);

    // Assert: no options argument, so no immediate refetch
    expect(router.applyChanges.mock.calls).toEqual([[[change]]]);
  });

  it.each(['injects', 'assets', undefined])('given_aBulkOperationEndOn_%s_should_invalidateEveryLiveQuery', (entity) => {
    // Arrange
    connectStreamToLiveRouter(router);

    // Act
    emitBulkEnd(entity);

    // Assert: a bulk operation streams no event per entity, whatever it changed
    expect(router.invalidateAllLive).toHaveBeenCalledTimes(1);
    expect(router.applyChanges).not.toHaveBeenCalled();
    expect(router.invalidateAll).not.toHaveBeenCalled();
    expect(router.resetAll).not.toHaveBeenCalled();
  });

  it.each([true, false])('given_anOpenWithFirst_%s_should_invalidateEveryLiveQuery', (first) => {
    // Arrange
    connectStreamToLiveRouter(router);

    // Act
    emitOpen(first);

    // Assert
    expect(router.invalidateAllLive).toHaveBeenCalledTimes(1);
    expect(router.applyChanges).not.toHaveBeenCalled();
  });

  it('given_theUnsubscribe_should_stopForwardingEveryEvent', () => {
    // Arrange
    const disconnect = connectStreamToLiveRouter(router);

    // Act
    disconnect();
    emitChange({
      type: 'tags',
      id: 't1',
      action: 'create',
    });
    emitBulkEnd('scenarios');
    emitOpen(false);

    // Assert
    expect(stream.entityHandlers.size + stream.bulkHandlers.size + stream.openHandlers.size).toBe(0);
    expect(router.applyChanges).not.toHaveBeenCalled();
    expect(router.invalidateAllLive).not.toHaveBeenCalled();
  });
});
