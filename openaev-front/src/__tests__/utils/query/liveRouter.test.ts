import { focusManager, QueryClient, type QueryKey, QueryObserver } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, type MockInstance, vi } from 'vitest';

import { type EntityChange } from '../../../utils/query/entityChange';
import { type LiveMeta } from '../../../utils/query/live';
import { createLiveRouter, type LiveRouter } from '../../../utils/query/liveRouter';

interface Item {
  id: string;
  version?: string;
}

const itemRefs = (type: string) => (data: unknown) => (data as Item[]).map(item => ({
  type,
  id: item.id,
}));

const deferred = <T>() => {
  let resolve: (value: T) => void = () => {};
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return {
    promise,
    resolve,
  };
};

const change = (type: string, id: string, action: EntityChange['action'], parents?: EntityChange['parents']): EntityChange => ({
  type,
  id,
  action,
  parents,
});

const assetUpdate = (id = 'a1') => change('assets', id, 'update');

describe('liveRouter', () => {
  let queryClient: QueryClient;
  let router: LiveRouter;
  let invalidate: MockInstance<QueryClient['invalidateQueries']>;
  const unsubscribes: (() => void)[] = [];

  // An unmounted query whose data was read a minute ago: past any throttle.
  const seed = (key: QueryKey, data: unknown, live?: LiveMeta) => {
    queryClient.getQueryCache().build(queryClient, {
      queryKey: key,
      meta: live ? { live } : undefined,
    });
    queryClient.setQueryData(key, data, { updatedAt: Date.now() - 60_000 });
  };

  // A mounted query whose every fetch waits for the test to answer it.
  const mount = (key: QueryKey, live: LiveMeta, options: { staleTime?: number } = {}) => {
    const responses: ReturnType<typeof deferred<unknown>>[] = [];
    const startedAt: number[] = [];
    const queryFn = vi.fn(() => {
      const response = deferred<unknown>();
      responses.push(response);
      startedAt.push(Date.now());
      return response.promise;
    });
    unsubscribes.push(new QueryObserver(queryClient, {
      queryKey: key,
      queryFn,
      meta: { live },
      ...options,
    }).subscribe(() => {}));
    return {
      queryFn,
      responses,
      startedAt,
      // Answers the latest fetch.
      respond: async (data: unknown) => {
        responses[responses.length - 1].resolve(data);
        await vi.advanceTimersByTimeAsync(0);
      },
    };
  };

  // Queries targeted by each invalidateQueries call, with the refetch type it asked for.
  const calls = () => invalidate.mock.calls.map(([filters]) => ({
    refetchType: filters?.refetchType,
    keys: queryClient.getQueryCache().getAll()
      .filter(query => filters?.predicate?.(query))
      .map(query => query.queryKey[0]),
  }));

  const invalidatedKeys = () => calls().flatMap(call => call.keys);

  beforeEach(() => {
    vi.useFakeTimers();
    focusManager.setFocused(true);
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    router = createLiveRouter(queryClient);
  });

  afterEach(() => {
    unsubscribes.splice(0).forEach(unsubscribe => unsubscribe());
    router.dispose();
    queryClient.unmount();
    queryClient.clear();
    focusManager.setFocused(undefined);
    vi.useRealTimers();
  });

  describe('matching', () => {
    it.each(['create', 'update', 'delete'] as const)('given_aKnownIdAnd_%s_should_invalidateTheQueryShowingIt', async (action) => {
      // Arrange
      seed(['list'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      seed(['other'], [{ id: 'z9' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([change('assets', 'a1', action)]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(queryClient.getQueryState(['list'])?.isInvalidated).toBe(true);
      expect(queryClient.getQueryState(['other'])?.isInvalidated).toBe(false);
    });

    it('given_aCreateOnACollectionType_should_invalidateTheCollectionQuery', async () => {
      // Arrange
      seed(['list'], [], { collections: ['assets'] });
      seed(['tags'], [], { collections: ['tags'] });

      // Act
      router.applyChanges([change('assets', 'new', 'create')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidatedKeys()).toEqual(['list']);
    });

    it('given_anUpdateOfAnUnknownId_should_matchNothing', async () => {
      // Arrange
      seed(['list'], [{ id: 'a1' }], {
        refs: itemRefs('assets'),
        collections: ['assets'],
      });

      // Act
      router.applyChanges([assetUpdate('unknown')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_aDeleteOfAnUnknownId_should_invalidateTheCollectionQueries', async () => {
      // Arrange: the deleted entity is off this page, but the total and the page shift
      seed(['list'], [{ id: 'a1' }], {
        refs: itemRefs('assets'),
        collections: ['assets'],
      });
      seed(['detail'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([change('assets', 'unknown', 'delete')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidatedKeys()).toEqual(['list']);
    });

    it('given_aQueryWithoutLiveMeta_should_neverBeInvalidated', async () => {
      // Arrange
      seed(['plain'], [{ id: 'a1' }]);

      // Act
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_aChangeWithParents_should_alsoInvalidateQueriesShowingAParent', async () => {
      // Arrange
      seed(['asset'], [{ id: 'asset-1' }], { refs: itemRefs('assets') });
      seed(['agents'], [{ id: 'agent-1' }], { refs: itemRefs('agents') });

      // Act
      router.applyChanges([change('agents', 'agent-1', 'update', [{
        type: 'assets',
        id: 'asset-1',
      }])]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidatedKeys().sort()).toEqual(['agents', 'asset']);
    });

    it('given_aCreateWithAParent_should_treatTheParentAsAnUpdateNotACreate', async () => {
      // Arrange
      seed(['assets-list'], [], { collections: ['assets'] });

      // Act
      router.applyChanges([change('agents', 'agent-1', 'create', [{
        type: 'assets',
        id: 'asset-1',
      }])]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_refsThatThrow_should_ignoreThatQueryAndKeepRouting', async () => {
      // Arrange
      const throwingRefs = () => {
        throw new Error('bad data');
      };
      seed(['broken'], [{ id: 'a1' }], { refs: throwingRefs });
      seed(['fine'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidatedKeys()).toEqual(['fine']);
    });

    it('given_aQueryWithoutDataYet_should_notCallRefs', async () => {
      // Arrange
      const refs = vi.fn(() => []);
      queryClient.getQueryCache().build(queryClient, {
        queryKey: ['pending'],
        meta: { live: { refs } },
      });

      // Act
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(refs).not.toHaveBeenCalled();
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_aCollectionQueryWhoseFirstLoadFailed_should_matchACreate', async () => {
      // Arrange: no data, only an error
      await queryClient.fetchQuery({
        queryKey: ['list'],
        queryFn: () => Promise.reject(new Error('down')),
        meta: { live: { collections: ['assets'] } },
      }).catch(() => undefined);

      // Act: past the throttle measured from the failure
      router.applyChanges([change('assets', 'new', 'create')]);
      await vi.advanceTimersByTimeAsync(1250);

      // Assert
      expect(invalidatedKeys()).toEqual(['list']);
    });

    it('given_anInactiveMatchingQuery_should_beMarkedStale', async () => {
      // Arrange
      seed(['list'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(queryClient.getQueryState(['list'])?.isInvalidated).toBe(true);
    });

    it.each(['create', 'update', 'delete'] as const)('given_a_%s_ofAWatchedTypeTheQueryDoesNotShow_should_invalidateIt', async (action) => {
      // Arrange: a count by status changes whichever entity of the type changes
      seed(['counts'], { total: 3 }, { watch: ['assets'] });
      seed(['list'], [{ id: 'a1' }], {
        refs: itemRefs('assets'),
        collections: ['assets'],
      });
      seed(['tags'], { total: 1 }, { watch: ['tags'] });

      // Act
      router.applyChanges([change('assets', 'elsewhere', action)]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(queryClient.getQueryState(['counts'])?.isInvalidated).toBe(true);
      expect(queryClient.getQueryState(['list'])?.isInvalidated).toBe(action !== 'update');
      expect(queryClient.getQueryState(['tags'])?.isInvalidated).toBe(false);
    });

    it('given_aWatchingQueryWithoutDataYet_should_invalidateItWithoutCallingRefs', async () => {
      // Arrange: built, not fetching, no data
      const refs = vi.fn(() => []);
      queryClient.getQueryCache().build(queryClient, {
        queryKey: ['counts'],
        meta: {
          live: {
            refs,
            watch: ['assets'],
          },
        },
      });

      // Act
      router.applyChanges([assetUpdate('elsewhere')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(queryClient.getQueryState(['counts'])?.isInvalidated).toBe(true);
      expect(refs).not.toHaveBeenCalled();
    });

    it('given_aParentOfAWatchedType_should_invalidateTheWatchingQuery', async () => {
      // Arrange
      seed(['counts'], { total: 3 }, { watch: ['assets'] });

      // Act
      router.applyChanges([change('agents', 'agent-1', 'update', [{
        type: 'assets',
        id: 'asset-1',
      }])]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(queryClient.getQueryState(['counts'])?.isInvalidated).toBe(true);
    });

    it('given_severalFlushesOnTheSameData_should_computeItsRefsOncePerDataObject', async () => {
      // Arrange
      const refs = vi.fn(itemRefs('assets'));
      seed(['list'], [{ id: 'a1' }], { refs });
      const nextData = [{ id: 'a2' }];

      // Act: three flushes on the same data, then new data and one more flush
      for (let i = 0; i < 3; i += 1) {
        router.applyChanges([assetUpdate('elsewhere')]);
        // eslint-disable-next-line no-await-in-loop -- each flush must run before the next change
        await vi.advanceTimersByTimeAsync(250);
      }
      queryClient.setQueryData(['list'], nextData);
      router.applyChanges([assetUpdate('elsewhere')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(refs).toHaveBeenCalledTimes(2);
      expect(refs.mock.calls[1][0]).toEqual(nextData);
    });
  });

  describe('batching', () => {
    it('given_changesInsideTheWindow_should_invalidateOnceAfterTheWindow', async () => {
      // Arrange
      seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      seed(['b'], [{ id: 'b1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([assetUpdate('a1')]);
      await vi.advanceTimersByTimeAsync(200);
      router.applyChanges([assetUpdate('b1')]);
      await vi.advanceTimersByTimeAsync(49);
      const beforeFlush = invalidate.mock.calls.length;
      await vi.advanceTimersByTimeAsync(1);

      // Assert
      expect(beforeFlush).toBe(0);
      expect(invalidate).toHaveBeenCalledTimes(1);
      expect(invalidatedKeys().sort()).toEqual(['a', 'b']);
    });

    it('given_dispose_should_dropBufferedChanges', async () => {
      // Arrange
      seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      router.applyChanges([assetUpdate()]);

      // Act
      router.dispose();
      await vi.advanceTimersByTimeAsync(5000);

      // Assert
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_dispose_should_cancelAPendingTrailingThrottleTimer', async () => {
      // Arrange: the second flush waits behind the throttle as a trailing run
      seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      const beforeDispose = invalidate.mock.calls.length;

      // Act
      router.dispose();
      await vi.advanceTimersByTimeAsync(5000);

      // Assert
      expect(beforeDispose).toBe(1);
      expect(invalidate).toHaveBeenCalledTimes(1);
    });
  });

  describe('throttle', () => {
    it('given_twoFlushesInsideTheThrottle_should_runASingleTrailingInvalidation', async () => {
      // Arrange
      seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      const duringThrottle = invalidate.mock.calls.length;
      await vi.advanceTimersByTimeAsync(500);

      // Assert
      expect(duringThrottle).toBe(1);
      expect(invalidate).toHaveBeenCalledTimes(2);
    });

    it('given_throttleMsOnTheQuery_should_overrideTheDefault', async () => {
      // Arrange
      seed(['fast'], [{ id: 'a1' }], {
        refs: itemRefs('assets'),
        throttleMs: 300,
      });
      seed(['slow'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      const afterSecondFlush = calls().map(call => call.keys);
      await vi.advanceTimersByTimeAsync(50);
      const afterFastThrottle = calls().map(call => call.keys);
      await vi.advanceTimersByTimeAsync(700);

      // Assert
      expect(afterSecondFlush).toEqual([['fast', 'slow']]);
      expect(afterFastThrottle).toEqual([['fast', 'slow'], ['fast']]);
      expect(calls().map(call => call.keys)).toEqual([['fast', 'slow'], ['fast'], ['slow']]);
    });

    it('given_aThrottleOnOneQuery_should_notDelayAnotherQuery', async () => {
      // Arrange
      seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      seed(['b'], [{ id: 'b1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([assetUpdate('a1')]);
      await vi.advanceTimersByTimeAsync(250);
      router.applyChanges([assetUpdate('b1')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(calls().map(call => call.keys)).toEqual([['a'], ['b']]);
    });

    it('given_aChangeShortlyAfterATrailingRun_should_waitForTheThrottleAgain', async () => {
      // Arrange: a leading run at 250, a trailing run at 1250
      seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(1000);
      const afterTrailingRun = invalidate.mock.calls.length;

      // Act: flushed at 1600, 350 ms after the trailing run
      await vi.advanceTimersByTimeAsync(100);
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      const atFlush = invalidate.mock.calls.length;
      await vi.advanceTimersByTimeAsync(649);
      const justBeforeThrottleEnd = invalidate.mock.calls.length;
      await vi.advanceTimersByTimeAsync(1);

      // Assert
      expect(afterTrailingRun).toBe(2);
      expect(atFlush).toBe(2);
      expect(justBeforeThrottleEnd).toBe(2);
      expect(invalidate).toHaveBeenCalledTimes(3);
    });

    it('given_aQueryRemovedAfterARun_should_notThrottleTheQueryReplacingIt', async () => {
      // Arrange: a run, then the query is garbage collected and mounted again with old data
      seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      const cache = queryClient.getQueryCache();
      cache.remove(cache.find({ queryKey: ['a'] })!);
      seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert: a leading run, not a trailing one 1 s after the first
      expect(invalidate).toHaveBeenCalledTimes(2);
    });

    it('given_aFetchThatJustFailed_should_measureTheThrottleFromTheFailure', async () => {
      // Arrange: no data, an error read just now
      await queryClient.fetchQuery({
        queryKey: ['list'],
        queryFn: () => Promise.reject(new Error('down')),
        meta: { live: { collections: ['assets'] } },
      }).catch(() => undefined);

      // Act: flushed 250 ms after the failure
      router.applyChanges([change('assets', 'new', 'create')]);
      await vi.advanceTimersByTimeAsync(999);
      const justBeforeTheThrottleEnd = queryClient.getQueryState(['list'])?.isInvalidated;
      await vi.advanceTimersByTimeAsync(1);

      // Assert
      expect(justBeforeTheThrottleEnd).toBe(false);
      expect(queryClient.getQueryState(['list'])?.isInvalidated).toBe(true);
    });

    it('given_aDeferredInvalidationOnceAFetchSettles_should_throttleAChangeRightAfterIt', async () => {
      // Arrange: a refetch in flight when every live query is invalidated, cancelled 5 s later (its
      // last observer left): the deferred invalidation runs, and the data stays the old one
      seed(['list'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      queryClient.fetchQuery({
        queryKey: ['list'],
        queryFn: () => new Promise<Item[]>(() => {}),
        staleTime: 0,
        meta: { live: { refs: itemRefs('assets') } },
      }).catch(() => undefined);
      router.invalidateAllLive();
      await vi.advanceTimersByTimeAsync(5000);
      await queryClient.cancelQueries({ queryKey: ['list'] });
      const afterTheDeferredRun = invalidate.mock.calls.length;

      // Act
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      const atFlush = invalidate.mock.calls.length;
      await vi.advanceTimersByTimeAsync(749);
      const justBeforeTheThrottleEnd = invalidate.mock.calls.length;
      await vi.advanceTimersByTimeAsync(1);

      // Assert: measured from the deferred run, not from the invalidation that deferred it
      expect(afterTheDeferredRun).toBe(1);
      expect(atFlush).toBe(1);
      expect(justBeforeTheThrottleEnd).toBe(1);
      expect(invalidate).toHaveBeenCalledTimes(2);
    });

    it('given_aQuerySlowerThanItsThrottleUnderSteadyChanges_should_leaveTheThrottleBetweenTwoFetches', async () => {
      // Arrange: every fetch takes 1.5 s, the throttle is 1 s
      const fetches: {
        start: number;
        end?: number;
      }[] = [];
      unsubscribes.push(new QueryObserver(queryClient, {
        queryKey: ['slow'],
        queryFn: () => {
          const fetch: (typeof fetches)[number] = { start: Date.now() };
          fetches.push(fetch);
          return new Promise<Item[]>((resolve) => {
            setTimeout(() => {
              fetch.end = Date.now();
              resolve([{ id: 'a1' }]);
            }, 1500);
          });
        },
        meta: { live: { refs: itemRefs('assets') } },
      }).subscribe(() => {}));

      // Act: a change on the entity it shows every 100 ms for 10 s
      for (let i = 0; i < 100; i += 1) {
        router.applyChanges([assetUpdate()]);
        // eslint-disable-next-line no-await-in-loop -- the clock must move between two changes
        await vi.advanceTimersByTimeAsync(100);
      }

      // Assert
      const gaps = fetches.slice(1).map((fetch, i) => fetch.start - fetches[i].end!);
      expect(fetches.length).toBeGreaterThanOrEqual(3);
      gaps.forEach(gap => expect(gap).toBeGreaterThanOrEqual(1000));
    });
  });

  describe('hidden tab', () => {
    it('given_aVisibleTab_should_refetchActiveQueries', async () => {
      // Arrange
      seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(calls()[0].refetchType).toBe('active');
    });

    it('given_aHiddenTab_should_onlyMarkAMountedQueryInvalidatedWithoutFetching', async () => {
      // Arrange
      queryClient.mount();
      const list = mount(['a'], { refs: itemRefs('assets') }, { staleTime: Number.POSITIVE_INFINITY });
      await list.respond([{ id: 'a1' }]);
      await vi.advanceTimersByTimeAsync(60_000);
      focusManager.setFocused(false);

      // Act
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(calls()[0].refetchType).toBe('none');
      expect(queryClient.getQueryState(['a'])?.isInvalidated).toBe(true);
      expect(list.queryFn).toHaveBeenCalledTimes(1);
    });

    it('given_aChangeWhileHidden_should_refetchOnFocus', async () => {
      // Arrange: never stale by time, so only the router's invalidation can refetch it on focus
      queryClient.mount();
      const list = mount(['a'], { refs: itemRefs('assets') }, { staleTime: Number.POSITIVE_INFINITY });
      await list.respond([{ id: 'a1' }]);
      await vi.advanceTimersByTimeAsync(60_000);
      focusManager.setFocused(false);
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);

      // Act
      focusManager.setFocused(true);
      await vi.advanceTimersByTimeAsync(0);

      // Assert
      expect(list.queryFn).toHaveBeenCalledTimes(2);
    });
  });

  describe('invalidateAllLive', () => {
    it('given_invalidateAllLive_should_invalidateEveryLiveQuery', () => {
      // Arrange
      seed(['a'], [], { collections: ['x'] });
      seed(['plain'], []);

      // Act
      router.invalidateAllLive();

      // Assert
      expect(invalidatedKeys()).toEqual(['a']);
    });

    it('given_aHiddenTab_should_onlyMarkStaleOnInvalidateAllLive', () => {
      // Arrange
      seed(['a'], [], { collections: ['x'] });
      focusManager.setFocused(false);

      // Act
      router.invalidateAllLive();

      // Assert
      expect(calls()[0].refetchType).toBe('none');
    });

    it('given_aRecentInvalidation_should_throttleInvalidateAllLive', async () => {
      // Arrange
      seed(['a'], [], { collections: ['x'] });

      // Act
      router.invalidateAllLive();
      router.invalidateAllLive();
      const immediate = invalidate.mock.calls.length;
      await vi.advanceTimersByTimeAsync(1000);

      // Assert
      expect(immediate).toBe(1);
      expect(invalidate).toHaveBeenCalledTimes(2);
    });
  });

  describe('in-flight fetches', () => {
    const live: LiveMeta = {
      refs: itemRefs('assets'),
      collections: ['assets'],
    };

    const refetchList = async () => {
      seed(['list'], [{ id: 'a1' }], live);
      const response = deferred<unknown>();
      const refetch = queryClient.fetchQuery({
        queryKey: ['list'],
        queryFn: () => response.promise,
        staleTime: 0,
        meta: { live },
      });
      return {
        response,
        refetch,
      };
    };

    it('given_aChangeOnAnEntityTheResponseShows_should_invalidateOnceTheFetchSettles', async () => {
      // Arrange
      const { response, refetch } = await refetchList();

      // Act: the response in flight may have been read before this change
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      const whileFetching = invalidate.mock.calls.length;
      response.resolve([{ id: 'a1' }]);
      await refetch;
      await vi.advanceTimersByTimeAsync(1000);

      // Assert
      expect(whileFetching).toBe(0);
      expect(invalidatedKeys()).toEqual(['list']);
    });

    it('given_aChangeOnAnEntityTheResponseDoesNotShow_should_notRefetch', async () => {
      // Arrange
      const { response, refetch } = await refetchList();

      // Act: a steady flow of unrelated changes must not keep refetching a slow query
      router.applyChanges([assetUpdate('elsewhere')]);
      await vi.advanceTimersByTimeAsync(250);
      response.resolve([{ id: 'a1' }]);
      await refetch;
      await vi.advanceTimersByTimeAsync(5000);

      // Assert
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_aStreamChangeWhileAMountedQueryFetches_should_letTheFetchFinish', async () => {
      // Arrange: a refetch in flight of data that shows the changed entity
      const list = mount(['list'], live);
      await list.respond([{ id: 'a1' }]);
      void queryClient.refetchQueries({ queryKey: ['list'] });

      // Act
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      await list.respond([{
        id: 'a1',
        version: 'in flight',
      }]);

      // Assert: not restarted, its response lands
      expect(list.queryFn).toHaveBeenCalledTimes(2);
      expect(queryClient.getQueryData(['list'])).toEqual([{
        id: 'a1',
        version: 'in flight',
      }]);
    });

    it.each([
      ['first', [assetUpdate('a1'), assetUpdate('elsewhere')]],
      ['second', [assetUpdate('elsewhere'), assetUpdate('a1')]],
    ])('given_twoBatchesParkedDuringOneFetchWhereOnlyThe_%s_matters_should_refetchAfterTheFetch', async (_which, [first, second]) => {
      // Arrange
      const list = mount(['list'], { refs: itemRefs('assets') });
      router.applyChanges([first]);
      await vi.advanceTimersByTimeAsync(250);
      router.applyChanges([second]);
      await vi.advanceTimersByTimeAsync(250);

      // Act: past the throttle measured from the settled fetch
      await list.respond([{ id: 'a1' }]);
      await vi.advanceTimersByTimeAsync(1000);

      // Assert
      expect(list.queryFn).toHaveBeenCalledTimes(2);
    });

    it('given_anInvalidationDeferredByAFetch_should_refetchAsSoonAsTheFetchSettles', async () => {
      // Arrange: the first load is in flight when every live query is invalidated (a stream open)
      const list = mount(['list'], live);
      router.invalidateAllLive();
      await vi.advanceTimersByTimeAsync(300);

      // Act
      await list.respond([{ id: 'a1' }]);
      const settledAt = Date.now();

      // Assert: no throttle on top
      expect(list.queryFn).toHaveBeenCalledTimes(2);
      expect(list.startedAt[1]).toBe(settledAt);
    });

    it('given_aQueryRemovedWhileChangesWereParkedForItsFetch_should_leaveNothingBehind', async () => {
      // Arrange: a change is parked for the fetch in flight, then the query leaves the cache
      const { response, refetch } = await refetchList();
      refetch.catch(() => {});
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      const cache = queryClient.getQueryCache();

      // Act: a new query with the same key settles after the removal
      cache.remove(cache.find({ queryKey: ['list'] })!);
      response.resolve([{ id: 'a1' }]);
      await vi.advanceTimersByTimeAsync(0);
      seed(['list'], [{ id: 'a1' }], live);
      await vi.advanceTimersByTimeAsync(5000);

      // Assert
      expect(invalidate).not.toHaveBeenCalled();
    });
  });

  describe('immediate changes', () => {
    it('given_anImmediateChange_should_invalidateWithoutWaitingForTheWindow', async () => {
      // Arrange
      seed(['list'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      const refreshed = router.applyChanges([change('assets', 'a1', 'delete')], { immediate: true });

      // Assert: the caller can wait for the refetch (a mutation stays pending until then)
      expect(invalidatedKeys()).toEqual(['list']);
      await expect(refreshed).resolves.toBeUndefined();
    });

    it('given_anImmediateChange_should_throttleTheStreamEchoThatFollows', async () => {
      // Arrange
      seed(['list'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      router.applyChanges([assetUpdate()], { immediate: true });

      // Act
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      const beforeTrailing = invalidate.mock.calls.length;
      await vi.advanceTimersByTimeAsync(1000);

      // Assert
      expect(beforeTrailing).toBe(1);
      expect(invalidate).toHaveBeenCalledTimes(2);
    });

    it('given_anImmediateChangeWhileAQueryFetches_should_restartTheFetchAndResolveWithItsResponse', async () => {
      // Arrange: a refetch in flight whose response predates the change
      const list = mount(['list'], { refs: itemRefs('assets') });
      await list.respond([{ id: 'a1' }]);
      void queryClient.refetchQueries({ queryKey: ['list'] });
      const staleResponse = list.responses[1];
      let refreshed = false;

      // Act
      const refresh = router.applyChanges([assetUpdate()], { immediate: true }).then(() => {
        refreshed = true;
      });
      staleResponse.resolve([{
        id: 'a1',
        version: 'before the change',
      }]);
      await vi.advanceTimersByTimeAsync(0);
      const refreshedBeforeTheNewResponse = refreshed;
      await list.respond([{
        id: 'a1',
        version: 'after the change',
      }]);
      await refresh;

      // Assert
      expect(list.queryFn).toHaveBeenCalledTimes(3);
      expect(refreshedBeforeTheNewResponse).toBe(false);
      expect(queryClient.getQueryData(['list'])).toEqual([{
        id: 'a1',
        version: 'after the change',
      }]);
    });

    it('given_anImmediateChange_should_cancelThePendingTrailingRun', async () => {
      // Arrange: a leading run, then a trailing run pending until 1250
      seed(['list'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);

      // Act
      await router.applyChanges([assetUpdate()], { immediate: true });
      await vi.advanceTimersByTimeAsync(5000);

      // Assert: the leading and the immediate runs only
      expect(invalidate).toHaveBeenCalledTimes(2);
    });

    it('given_anImmediateChangeMatchingAQueryInItsFirstLoad_should_refetchItOnceThatLoadSettles', async () => {
      // Arrange: a first load in flight (TanStack never restarts one), matched by a watched type
      const counts = mount(['counts'], { watch: ['assets'] });

      // Act: the first load's response predates the change
      const refreshed = router.applyChanges([assetUpdate()], { immediate: true });
      await counts.respond('before the change');
      await counts.respond('after the change');
      await refreshed;

      // Assert
      expect(counts.queryFn).toHaveBeenCalledTimes(2);
      expect(queryClient.getQueryData(['counts'])).toBe('after the change');
    });

    it('given_anImmediateChangeAQueryFetchingWithoutDataMayShow_should_matchItsResponseOnceSettled', async () => {
      // Arrange: a first load in flight, nothing to match yet
      const list = mount(['list'], { refs: itemRefs('assets') });

      // Act
      await router.applyChanges([assetUpdate()], { immediate: true });
      await list.respond([{ id: 'a1' }]);
      await vi.advanceTimersByTimeAsync(1000);

      // Assert
      expect(list.queryFn).toHaveBeenCalledTimes(2);
    });
  });

  describe('resetAll', () => {
    it('given_activeAndInactiveQueries_should_dropTheirDataAndRefetchTheActiveOnes', async () => {
      // Arrange
      const active = mount(['active'], { collections: ['assets'] });
      await active.respond(['active data']);
      seed(['inactive'], ['inactive data'], { collections: ['assets'] });

      // Act
      void router.resetAll();
      await vi.advanceTimersByTimeAsync(0);

      // Assert
      expect(queryClient.getQueryData(['inactive'])).toBeUndefined();
      expect(queryClient.getQueryData(['active'])).toBeUndefined();
      expect(active.queryFn).toHaveBeenCalledTimes(2);
    });

    it('given_aFetchInFlight_should_neverStoreItsResponse', async () => {
      // Arrange: a response read with the previous rights
      const list = mount(['list'], { collections: ['assets'] });
      const previousRights = list.responses[0];

      // Act
      void router.resetAll();
      previousRights.resolve('read with the previous rights');
      await vi.advanceTimersByTimeAsync(0);
      const dataAfterTheOldResponse = queryClient.getQueryData(['list']);
      await list.respond('read with the current rights');

      // Assert
      expect(dataAfterTheOldResponse).toBeUndefined();
      expect(queryClient.getQueryData(['list'])).toBe('read with the current rights');
    });

    it('given_pendingBatchTrailingRunsAndDeferredChanges_should_dropThemAll', async () => {
      // Arrange: unmounted queries, so that only the router can invalidate them after the reset
      const fetchForever = (key: string, live: LiveMeta) => {
        queryClient.fetchQuery({
          queryKey: [key],
          queryFn: () => new Promise<never>(() => {}),
          meta: { live },
        }).catch(() => undefined);
      };
      seed(['throttled'], { total: 1 }, { watch: ['assets'] });
      fetchForever('due', { refs: itemRefs('assets') });
      // A leading run for 'throttled', an invalidation due once the fetch of 'due' settles
      router.invalidateAllLive();
      fetchForever('parked', { watch: ['assets'] });
      // A trailing run pending for 'throttled', the change parked for the fetches of 'due' and 'parked'
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);
      // A change still in the batch window
      router.applyChanges([assetUpdate()]);
      const invalidatedBeforeTheReset = queryClient.getQueryState(['throttled'])?.isInvalidated;

      // Act: every fetch is cancelled and every query reset
      void router.resetAll();
      await vi.advanceTimersByTimeAsync(5000);

      // Assert
      expect(invalidatedBeforeTheReset).toBe(true);
      expect(queryClient.getQueryState(['throttled'])?.isInvalidated).toBe(false);
      expect(queryClient.getQueryState(['due'])?.isInvalidated).toBe(false);
      expect(queryClient.getQueryState(['parked'])?.isInvalidated).toBe(false);
    });
  });

  describe('invalidateAll', () => {
    it('given_activeInactiveAndPlainQueries_should_invalidateThemAllKeepingTheirDataAndRefetchTheActiveOnes', async () => {
      // Arrange
      const active = mount(['active'], { collections: ['assets'] });
      await active.respond('active data');
      seed(['inactive'], 'inactive data', { collections: ['assets'] });
      seed(['plain'], 'plain data');

      // Act
      void router.invalidateAll();
      await vi.advanceTimersByTimeAsync(0);

      // Assert: the data stays on screen while the active query refetches
      expect(active.queryFn).toHaveBeenCalledTimes(2);
      expect(queryClient.getQueryData(['active'])).toBe('active data');
      expect(queryClient.getQueryState(['inactive'])).toMatchObject({
        data: 'inactive data',
        isInvalidated: true,
      });
      expect(queryClient.getQueryState(['plain'])).toMatchObject({
        data: 'plain data',
        isInvalidated: true,
      });
    });

    it('given_aFirstLoadInFlight_should_cancelItSoThatItsResponseNeverLands', async () => {
      // Arrange: a response read with the previous rights, which a refetch would not restart
      const list = mount(['list'], { collections: ['assets'] });
      const previousRights = list.responses[0];

      // Act
      void router.invalidateAll();
      await vi.advanceTimersByTimeAsync(0);
      previousRights.resolve('read with the previous rights');
      await vi.advanceTimersByTimeAsync(0);
      const dataAfterTheOldResponse = queryClient.getQueryData(['list']);
      await list.respond('read with the current rights');

      // Assert
      expect(dataAfterTheOldResponse).toBeUndefined();
      expect(list.queryFn).toHaveBeenCalledTimes(2);
      expect(queryClient.getQueryData(['list'])).toBe('read with the current rights');
    });

    it('given_aPendingTrailingRun_should_dropIt', async () => {
      // Arrange: a trailing run pending until 1 s after the first load
      const list = mount(['list'], { refs: itemRefs('assets') });
      await list.respond([{ id: 'a1' }]);
      router.applyChanges([assetUpdate()]);
      await vi.advanceTimersByTimeAsync(250);

      // Act
      void router.invalidateAll();
      await vi.advanceTimersByTimeAsync(0);
      await list.respond([{ id: 'a1' }]);
      await vi.advanceTimersByTimeAsync(5000);

      // Assert: the first load and the refetch of invalidateAll only
      expect(list.queryFn).toHaveBeenCalledTimes(2);
    });

    it('given_aHiddenTab_should_onlyMarkTheQueriesInvalidated', async () => {
      // Arrange
      const list = mount(['list'], { collections: ['assets'] }, { staleTime: Number.POSITIVE_INFINITY });
      await list.respond('data');
      focusManager.setFocused(false);

      // Act
      void router.invalidateAll();
      await vi.advanceTimersByTimeAsync(0);

      // Assert
      expect(list.queryFn).toHaveBeenCalledTimes(1);
      expect(queryClient.getQueryState(['list'])?.isInvalidated).toBe(true);
    });
  });
});
