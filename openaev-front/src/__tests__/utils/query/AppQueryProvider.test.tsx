import '@testing-library/jest-dom/vitest';

import { type QueryClient, type QueryFunction, QueryObserver, useQuery, useQueryClient } from '@tanstack/react-query';
import { act, cleanup, render, screen } from '@testing-library/react';
import { StrictMode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { DATA_UPDATE_SUCCESS } from '../../../constants/ActionTypes';
import AppQueryProvider from '../../../utils/query/AppQueryProvider';
import { type LiveRouter } from '../../../utils/query/liveRouter';
import { useLiveRouter } from '../../../utils/query/liveRouterContext';
import { type SessionRights } from '../../../utils/query/sessionRights';
import type * as ConnectStreamModule from '../../../utils/stream/connectStreamToLiveRouter';
import { startStream, stopStream } from '../../../utils/stream/streamClient';
import FakeEventSource from '../stream/fakeEventSource';

const stream = vi.hoisted(() => ({
  routers: [] as LiveRouter[],
  disconnect: vi.fn(),
}));

// The real connection to the stream client, recorded.
vi.mock('../../../utils/stream/connectStreamToLiveRouter', async (importOriginal) => {
  const actual = await importOriginal<typeof ConnectStreamModule>();
  return {
    default: (router: LiveRouter) => {
      stream.routers.push(router);
      const disconnect = actual.default(router);
      return () => {
        stream.disconnect();
        disconnect();
      };
    },
  };
});
vi.mock('../../../utils/Action', () => ({
  buildUri: (path: string) => path,
  simpleCall: vi.fn(),
}));
vi.mock('../../../utils/error/errorHandlerUtil', () => ({ notifyErrorHandler: vi.fn() }));
vi.mock('@tanstack/react-query-devtools', () => ({ ReactQueryDevtools: () => null }));

const rights = (overrides: Partial<SessionRights> = {}): SessionRights => ({
  admin: false,
  capabilities: ['ACCESS_ASSETS', 'MANAGE_ASSETS'],
  grants: { 'scenario-1': 'OBSERVER' },
  ...overrides,
});

const grownRights = () => rights({ capabilities: ['ACCESS_ASSETS', 'MANAGE_ASSETS', 'ACCESS_TAGS'] });
const shrunkRights = () => rights({ capabilities: ['ACCESS_ASSETS'] });

let capturedClient: QueryClient;
let capturedRouter: LiveRouter;
const Probe = () => {
  capturedClient = useQueryClient();
  capturedRouter = useLiveRouter();
  return null;
};

const tree = (sessionRights?: SessionRights) => (
  <AppQueryProvider rights={sessionRights}>
    <Probe />
  </AppQueryProvider>
);

const mount = (sessionRights?: SessionRights) => render(tree(sessionRights));

// A live query showing the asset 'a1'.
const LiveAsset = ({ queryFn }: { queryFn: QueryFunction<string> }) => {
  const { data } = useQuery({
    queryKey: ['assets', 'a1'],
    queryFn,
    meta: {
      live: {
        refs: () => [{
          type: 'assets',
          id: 'a1',
        }],
      },
    },
  });
  return <span>{data ?? 'loading'}</span>;
};

describe('AppQueryProvider', () => {
  const unsubscribes: (() => void)[] = [];

  // One query mounted (each fetch answers its own value) and one in the cache only.
  const seedQueries = async () => {
    const queryFn = vi.fn()
      .mockResolvedValueOnce('first answer')
      .mockResolvedValue('second answer');
    unsubscribes.push(new QueryObserver(capturedClient, {
      queryKey: ['active'],
      queryFn,
    }).subscribe(() => {}));
    await capturedClient.fetchQuery({
      queryKey: ['inactive'],
      queryFn: () => Promise.resolve('cached'),
    });
    await act(() => vi.advanceTimersByTimeAsync(0));
    return queryFn;
  };

  beforeEach(() => {
    vi.useFakeTimers();
    FakeEventSource.reset();
    vi.stubGlobal('EventSource', FakeEventSource);
    stream.routers = [];
    stream.disconnect.mockReset();
  });

  afterEach(() => {
    unsubscribes.splice(0).forEach(unsubscribe => unsubscribe());
    cleanup();
    stopStream();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  describe('stream', () => {
    it('given_aMount_should_connectTheStreamToTheRouterItProvides', () => {
      // Act
      mount(rights());

      // Assert
      expect(stream.routers).toEqual([capturedRouter]);
    });

    it('given_anUnmount_should_disconnectTheStreamAndDisposeTheRouter', () => {
      // Arrange
      const { unmount } = mount(rights());
      const dispose = vi.spyOn(capturedRouter, 'dispose');

      // Act
      unmount();

      // Assert
      expect(stream.disconnect).toHaveBeenCalledTimes(1);
      expect(dispose).toHaveBeenCalledTimes(1);
    });

    it('given_aStrictModeMount_should_keepTheCacheAndStillRouteStreamChanges', async () => {
      // Arrange: StrictMode unmounts and mounts the provider again at once
      startStream();
      FakeEventSource.last.emit('open');
      const queryFn = vi.fn<QueryFunction<string>>()
        .mockResolvedValueOnce('before the change')
        .mockResolvedValue('after the change');
      render(
        <StrictMode>
          <AppQueryProvider rights={rights()}>
            <Probe />
            <LiveAsset queryFn={queryFn} />
          </AppQueryProvider>
        </StrictMode>,
      );
      await act(() => vi.advanceTimersByTimeAsync(0));
      const callsAfterTheMount = queryFn.mock.calls.length;
      const shownAfterTheMount = screen.getByText(/the change/).textContent;

      // Act: past the batch window and the throttle
      FakeEventSource.last.emit('message', {
        event_type: DATA_UPDATE_SUCCESS,
        attribute_id: 'asset_id',
        attribute_schema: 'assets',
        instance: { asset_id: 'a1' },
        listened: true,
      });
      await act(() => vi.advanceTimersByTimeAsync(1250));

      // Assert
      expect(callsAfterTheMount).toBe(1);
      expect(shownAfterTheMount).toBe('before the change');
      expect(capturedClient.getQueryCache().getAll()).toHaveLength(1);
      expect(queryFn).toHaveBeenCalledTimes(2);
      expect(screen.getByText('after the change')).toBeInTheDocument();
    });
  });

  describe('unmount', () => {
    it('given_anUnmount_should_dropTheCacheAndCancelItsFetchesOnceTheTimersRun', async () => {
      // Arrange: a fetch in flight
      const { unmount } = mount(rights());
      const signals: AbortSignal[] = [];
      unsubscribes.push(new QueryObserver(capturedClient, {
        queryKey: ['loading'],
        queryFn: ({ signal }) => {
          signals.push(signal);
          return new Promise<string>(() => {});
        },
      }).subscribe(() => {}));
      const client = capturedClient;

      // Act
      unmount();
      const queriesBeforeTheTimers = client.getQueryCache().getAll().length;
      await vi.advanceTimersByTimeAsync(0);

      // Assert: deferred, so that StrictMode's simulated unmount keeps the cache
      expect(queriesBeforeTheTimers).toBe(1);
      expect(client.getQueryCache().getAll()).toHaveLength(0);
      expect(signals).toHaveLength(1);
      expect(signals[0].aborted).toBe(true);
    });

    it('given_aRemount_should_startWithAnEmptyCacheAndANewRouter', async () => {
      // Arrange
      const first = mount();
      await seedQueries();
      const firstClient = capturedClient;
      const firstRouter = capturedRouter;

      // Act
      first.unmount();
      mount();

      // Assert
      expect(capturedClient).not.toBe(firstClient);
      expect(capturedRouter).not.toBe(firstRouter);
      expect(capturedClient.getQueryCache().getAll()).toHaveLength(0);
    });
  });

  describe('rights', () => {
    it('given_aRerenderWithTheSameRightsInAnotherOrder_should_keepEveryQuery', async () => {
      // Arrange
      const { rerender } = mount(rights());
      const queryFn = await seedQueries();

      // Act: a new object, capabilities in another order
      rerender(tree(rights({ capabilities: ['MANAGE_ASSETS', 'ACCESS_ASSETS'] })));
      await act(() => vi.advanceTimersByTimeAsync(0));

      // Assert
      expect(queryFn).toHaveBeenCalledTimes(1);
      expect(capturedClient.getQueryState(['inactive'])).toMatchObject({
        data: 'cached',
        isInvalidated: false,
      });
    });

    it('given_grownRights_should_keepEveryQueryDataAndRefetchTheMountedOnes', async () => {
      // Arrange
      const { rerender } = mount(rights());
      const queryFn = await seedQueries();
      const client = capturedClient;

      // Act
      rerender(tree(grownRights()));
      await act(() => vi.advanceTimersByTimeAsync(0));

      // Assert: same cache, invalidated
      expect(capturedClient).toBe(client);
      expect(capturedClient.getQueryState(['inactive'])).toMatchObject({
        data: 'cached',
        isInvalidated: true,
      });
      expect(queryFn).toHaveBeenCalledTimes(2);
      expect(capturedClient.getQueryData(['active'])).toBe('second answer');
    });

    it('given_shrunkRights_should_dropEveryQueryDataAndRefetchTheMountedOnes', async () => {
      // Arrange
      const { rerender } = mount(rights());
      const queryFn = await seedQueries();
      const client = capturedClient;

      // Act
      rerender(tree(shrunkRights()));
      await act(() => vi.advanceTimersByTimeAsync(0));

      // Assert: same cache, emptied
      expect(capturedClient).toBe(client);
      expect(capturedClient.getQueryData(['inactive'])).toBeUndefined();
      expect(queryFn).toHaveBeenCalledTimes(2);
      expect(capturedClient.getQueryData(['active'])).toBe('second answer');
    });

    it.each([
      ['grown', grownRights],
      ['shrunk', shrunkRights],
    ])('given_rights_%s_duringAFirstLoad_should_notKeepTheResponseReadWithThePreviousRights', async (_label, nextRights) => {
      // Arrange: a query loading with the previous rights
      const { rerender } = mount(rights());
      const queryFn = vi.fn()
        .mockImplementationOnce(() => new Promise<string>((resolve) => {
          setTimeout(() => resolve('previous rights'), 100);
        }))
        .mockResolvedValue('current rights');
      unsubscribes.push(new QueryObserver(capturedClient, {
        queryKey: ['loading'],
        queryFn,
      }).subscribe(() => {}));

      // Act
      rerender(tree(nextRights()));
      await act(() => vi.advanceTimersByTimeAsync(200));

      // Assert
      expect(queryFn).toHaveBeenCalledTimes(2);
      expect(capturedClient.getQueryData(['loading'])).toBe('current rights');
    });
  });
});
