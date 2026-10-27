import { type QueryClient, QueryObserver, useQueryClient } from '@tanstack/react-query';
import { act, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import AppQueryProvider from '../../../utils/query/AppQueryProvider';
import { type EntityChange } from '../../../utils/stream/entityChange';

const stream = vi.hoisted(() => ({
  entityHandlers: new Set<(change: EntityChange) => void>(),
  bulkHandlers: new Set<(entity: string | undefined) => void>(),
  openHandlers: new Set<(info: { first: boolean }) => void>(),
  lostHandlers: new Set<() => void>(),
  healthy: true,
}));

vi.mock('../../../utils/stream/streamClient', () => {
  const subscribe = <T,>(handlers: Set<T>) => (handler: T) => {
    handlers.add(handler);
    return () => handlers.delete(handler);
  };
  return {
    onEntityChange: subscribe(stream.entityHandlers),
    onBulkOperationEnd: subscribe(stream.bulkHandlers),
    onConnectionOpen: subscribe(stream.openHandlers),
    onConnectionLost: subscribe(stream.lostHandlers),
    isStreamHealthy: () => stream.healthy,
    isStreamStarted: () => true,
  };
});

vi.mock('@tanstack/react-query-devtools', () => ({ ReactQueryDevtools: () => null }));

let capturedClient: QueryClient;
const Probe = () => {
  capturedClient = useQueryClient();
  return null;
};

const tree = (rightsKey: string) => (
  <AppQueryProvider rightsKey={rightsKey}>
    <Probe />
  </AppQueryProvider>
);

const mount = (rightsKey = 'rights') => render(tree(rightsKey));

const seed = async (key: string, live: { collections?: string[] }) => {
  await capturedClient.fetchQuery({
    queryKey: [key],
    queryFn: () => Promise.resolve([]),
    meta: { live },
  });
};

const isStale = (key: string) => capturedClient.getQueryState([key])?.isInvalidated === true;

describe('AppQueryProvider', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    stream.entityHandlers.clear();
    stream.bulkHandlers.clear();
    stream.openHandlers.clear();
    stream.lostHandlers.clear();
    stream.healthy = true;
  });

  it.each([true, false])('given_anOpenWithFirst_%s_should_invalidateEveryLiveQuery', async (first) => {
    // Arrange
    mount();
    await seed('list', { collections: ['assets'] });

    // Act
    act(() => stream.openHandlers.forEach(handler => handler({ first })));

    // Assert
    expect(isStale('list')).toBe(true);
  });

  it('given_theFirstOpenWhileAQueryIsFetching_should_refetchItOnceTheFetchSettles', async () => {
    // Arrange: the query started loading while the stream was still connecting
    mount();
    let resolveFirst: (value: string[]) => void = () => {};
    const queryFn = vi.fn()
      .mockImplementationOnce(() => new Promise<string[]>((resolve) => {
        resolveFirst = resolve;
      }))
      .mockResolvedValue(['fresh']);
    const unsubscribe = new QueryObserver(capturedClient, {
      queryKey: ['loading'],
      queryFn,
      meta: { live: { collections: ['assets'] } },
    }).subscribe(() => {});

    // Act
    act(() => stream.openHandlers.forEach(handler => handler({ first: true })));
    resolveFirst(['read before the open']);
    await act(() => vi.advanceTimersByTimeAsync(1000));
    unsubscribe();

    // Assert
    expect(queryFn).toHaveBeenCalledTimes(2);
  });

  it('given_aLostConnection_should_markEveryLiveQueryStale', async () => {
    // Arrange
    mount();
    await seed('list', { collections: ['assets'] });

    // Act
    act(() => stream.lostHandlers.forEach(handler => handler()));

    // Assert
    expect(isStale('list')).toBe(true);
  });

  it('given_aLostConnectionWithAnUnhealthyStream_should_pollAMountedLiveQuery', async () => {
    // Arrange
    mount();
    const queryFn = vi.fn(() => Promise.resolve(['data']));
    const unsubscribe = new QueryObserver(capturedClient, {
      queryKey: ['polled'],
      queryFn,
      meta: { live: { collections: ['assets'] } },
    }).subscribe(() => {});
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(queryFn).toHaveBeenCalledTimes(1);

    // Act
    stream.healthy = false;
    act(() => stream.lostHandlers.forEach(handler => handler()));
    await act(() => vi.advanceTimersByTimeAsync(40_000));
    unsubscribe();

    // Assert
    expect(queryFn.mock.calls.length).toBeGreaterThan(1);
  });

  it('given_aHealthyStream_should_notPollAMountedLiveQuery', async () => {
    // Arrange
    mount();
    const queryFn = vi.fn(() => Promise.resolve(['data']));
    const unsubscribe = new QueryObserver(capturedClient, {
      queryKey: ['not-polled'],
      queryFn,
      meta: { live: { collections: ['assets'] } },
    }).subscribe(() => {});

    // Act
    await act(() => vi.advanceTimersByTimeAsync(60_000));
    unsubscribe();

    // Assert
    expect(queryFn).toHaveBeenCalledTimes(1);
  });

  it('given_anEntityChange_should_routeItToMatchingQueries', async () => {
    // Arrange
    mount();
    await seed('assets', { collections: ['assets'] });
    await seed('tags', { collections: ['tags'] });

    // Act
    stream.entityHandlers.forEach(handler => handler({
      type: 'assets',
      id: 'a1',
      action: 'create',
    }));
    await act(() => vi.advanceTimersByTimeAsync(250));

    // Assert
    expect(isStale('assets')).toBe(true);
    expect(isStale('tags')).toBe(false);
  });

  it('given_aBulkOperationOnKnownEntity_should_invalidateOnlyItsTypes', async () => {
    // Arrange
    mount();
    await seed('scenarios', { collections: ['scenarios'] });
    await seed('tags', { collections: ['tags'] });

    // Act
    act(() => stream.bulkHandlers.forEach(handler => handler('scenarios')));

    // Assert
    expect(isStale('scenarios')).toBe(true);
    expect(isStale('tags')).toBe(false);
  });

  it.each([undefined, 'something unmapped'])('given_aBulkOperationWithEntity_%s_should_invalidateEveryLiveQuery', async (entity) => {
    // Arrange
    mount();
    await seed('tags', { collections: ['tags'] });

    // Act
    act(() => stream.bulkHandlers.forEach(handler => handler(entity)));

    // Assert
    expect(isStale('tags')).toBe(true);
  });

  it('given_anUnmount_should_unsubscribeFromTheStream', () => {
    // Arrange
    const { unmount } = mount();
    expect(stream.entityHandlers.size).toBe(1);

    // Act
    unmount();

    // Assert
    expect(stream.entityHandlers.size).toBe(0);
    expect(stream.bulkHandlers.size).toBe(0);
    expect(stream.openHandlers.size).toBe(0);
  });

  it('given_aRightsChange_should_invalidateEveryQueryAndKeepTheCache', async () => {
    // Arrange: one live query and one that is not
    const { rerender } = mount('rights-1');
    await seed('list', { collections: ['assets'] });
    await capturedClient.fetchQuery({
      queryKey: ['plain'],
      queryFn: () => Promise.resolve([]),
    });
    const client = capturedClient;

    // Act
    rerender(tree('rights-2'));
    await act(() => vi.advanceTimersByTimeAsync(0));

    // Assert
    expect(capturedClient).toBe(client);
    expect(isStale('list')).toBe(true);
    expect(isStale('plain')).toBe(true);
  });

  it('given_aRightsChangeDuringAFetch_should_notKeepTheResponseReadWithThePreviousRights', async () => {
    // Arrange: a query loading with the previous rights
    const { rerender } = mount('rights-1');
    const queryFn = vi.fn()
      .mockImplementationOnce(() => new Promise<string>((resolve) => {
        setTimeout(() => resolve('previous rights'), 100);
      }))
      .mockResolvedValue('current rights');
    const unsubscribe = new QueryObserver(capturedClient, {
      queryKey: ['loading'],
      queryFn,
    }).subscribe(() => {});

    // Act
    rerender(tree('rights-2'));
    await act(() => vi.advanceTimersByTimeAsync(200));
    unsubscribe();

    // Assert
    expect(queryFn).toHaveBeenCalledTimes(2);
    expect(capturedClient.getQueryData(['loading'])).toBe('current rights');
  });

  it('given_aRerenderWithTheSameRights_should_invalidateNothing', async () => {
    // Arrange
    const { rerender } = mount('rights-1');
    await seed('list', { collections: ['assets'] });

    // Act
    rerender(tree('rights-1'));

    // Assert
    expect(isStale('list')).toBe(false);
  });

  it('given_aRemount_should_startWithAnEmptyCache', async () => {
    // Arrange
    const first = mount();
    await seed('list', { collections: ['assets'] });
    const firstClient = capturedClient;

    // Act
    first.unmount();
    mount();

    // Assert
    expect(capturedClient).not.toBe(firstClient);
    expect(capturedClient.getQueryCache().getAll()).toHaveLength(0);
  });
});
