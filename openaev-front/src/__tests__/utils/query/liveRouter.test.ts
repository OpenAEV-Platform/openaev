import { QueryClient, type QueryKey } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, type MockInstance, vi } from 'vitest';

import { type LiveMeta } from '../../../utils/query/live';
import { createLiveRouter, type LiveRouter } from '../../../utils/query/liveRouter';
import { type EntityChange } from '../../../utils/stream/entityChange';

interface Item { id: string }

const itemRefs = (type: string) => (data: unknown) => (data as Item[]).map(item => ({
  type,
  id: item.id,
}));

const setHidden = (hidden: boolean) => {
  Object.defineProperty(document, 'hidden', {
    configurable: true,
    value: hidden,
  });
};

describe('liveRouter', () => {
  let queryClient: QueryClient;
  let router: LiveRouter;
  let invalidate: MockInstance<QueryClient['invalidateQueries']>;

  const seed = async (key: QueryKey, data: unknown, live?: LiveMeta) => {
    await queryClient.fetchQuery({
      queryKey: key,
      queryFn: () => Promise.resolve(data),
      meta: live ? { live } : undefined,
    });
  };

  // Queries targeted by each invalidateQueries call, with the refetch type it asked for.
  const calls = () => invalidate.mock.calls.map(([filters]) => ({
    refetchType: filters?.refetchType,
    keys: queryClient.getQueryCache().getAll()
      .filter(query => filters?.predicate?.(query))
      .map(query => query.queryKey[0]),
  }));

  const invalidatedKeys = () => calls().flatMap(call => call.keys);

  const change = (type: string, id: string, action: EntityChange['action'], root?: EntityChange['root']): EntityChange => ({
    type,
    id,
    action,
    root,
  });

  beforeEach(() => {
    vi.useFakeTimers();
    setHidden(false);
    queryClient = new QueryClient();
    invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    router = createLiveRouter(queryClient);
  });

  afterEach(() => {
    router.dispose();
    queryClient.clear();
    vi.useRealTimers();
    setHidden(false);
  });

  describe('matching', () => {
    it('given_aKnownId_should_invalidateForAnyAction', async () => {
      // Arrange
      await seed(['list'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      await seed(['other'], [{ id: 'z9' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([change('assets', 'a1', 'delete')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidatedKeys()).toEqual(['list']);
    });

    it('given_aCreateOnACollectionType_should_invalidateTheCollectionQuery', async () => {
      // Arrange
      await seed(['list'], [], { collections: ['assets'] });
      await seed(['tags'], [], { collections: ['tags'] });

      // Act
      router.applyChanges([change('assets', 'new', 'create')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidatedKeys()).toEqual(['list']);
    });

    it('given_anUpdateOfAnUnknownId_should_matchNothing', async () => {
      // Arrange
      await seed(['list'], [{ id: 'a1' }], {
        refs: itemRefs('assets'),
        collections: ['assets'],
      });

      // Act
      router.applyChanges([change('assets', 'unknown', 'update')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_aDeleteOfAnUnknownId_should_invalidateTheCollectionQueries', async () => {
      // Arrange: the deleted entity is off this page, but the total and the page shift
      await seed(['list'], [{ id: 'a1' }], {
        refs: itemRefs('assets'),
        collections: ['assets'],
      });
      await seed(['detail'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([change('assets', 'unknown', 'delete')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidatedKeys()).toEqual(['list']);
    });

    it('given_aQueryWithoutLiveMeta_should_neverBeInvalidated', async () => {
      // Arrange
      await seed(['plain'], [{ id: 'a1' }]);

      // Act
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_aChangeWithARoot_should_alsoInvalidateQueriesShowingTheRoot', async () => {
      // Arrange
      await seed(['asset'], [{ id: 'asset-1' }], { refs: itemRefs('assets') });
      await seed(['agents'], [{ id: 'agent-1' }], { refs: itemRefs('agents') });

      // Act
      router.applyChanges([change('agents', 'agent-1', 'update', {
        type: 'assets',
        id: 'asset-1',
      })]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidatedKeys().sort()).toEqual(['agents', 'asset']);
    });

    it('given_aCreateWithARoot_should_treatTheRootAsAnUpdateNotACreate', async () => {
      // Arrange
      await seed(['assets-list'], [], { collections: ['assets'] });

      // Act
      router.applyChanges([change('agents', 'agent-1', 'create', {
        type: 'assets',
        id: 'asset-1',
      })]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_refsThatThrow_should_ignoreThatQueryAndKeepRouting', async () => {
      // Arrange
      const throwingRefs = () => {
        throw new Error('bad data');
      };
      await seed(['broken'], [{ id: 'a1' }], { refs: throwingRefs });
      await seed(['fine'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([change('assets', 'a1', 'update')]);
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
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(refs).not.toHaveBeenCalled();
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_anInactiveMatchingQuery_should_beMarkedStale', async () => {
      // Arrange
      await seed(['list'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(queryClient.getQueryState(['list'])?.isInvalidated).toBe(true);
    });
  });

  describe('batching', () => {
    it('given_changesInsideTheWindow_should_invalidateOnceAfterTheWindow', async () => {
      // Arrange
      await seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      await seed(['b'], [{ id: 'b1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(200);
      router.applyChanges([change('assets', 'b1', 'update')]);
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
      await seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      router.applyChanges([change('assets', 'a1', 'update')]);

      // Act
      router.dispose();
      await vi.advanceTimersByTimeAsync(5000);

      // Assert
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_dispose_should_cancelAPendingTrailingThrottleTimer', async () => {
      // Arrange: the second flush is parked behind the throttle as a trailing run
      await seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);
      router.applyChanges([change('assets', 'a1', 'update')]);
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
      await seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);
      const duringThrottle = invalidate.mock.calls.length;
      await vi.advanceTimersByTimeAsync(500);

      // Assert
      expect(duringThrottle).toBe(1);
      expect(invalidate).toHaveBeenCalledTimes(2);
    });

    it('given_throttleMsOnTheQuery_should_overrideTheDefault', async () => {
      // Arrange
      await seed(['fast'], [{ id: 'a1' }], {
        refs: itemRefs('assets'),
        throttleMs: 300,
      });
      await seed(['slow'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);
      router.applyChanges([change('assets', 'a1', 'update')]);
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
      await seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      await seed(['b'], [{ id: 'b1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);
      router.applyChanges([change('assets', 'b1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(calls().map(call => call.keys)).toEqual([['a'], ['b']]);
    });
  });

  describe('hidden tab', () => {
    it('given_aVisibleTab_should_refetchActiveQueries', async () => {
      // Arrange
      await seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(calls()[0].refetchType).toBe('active');
    });

    it('given_aHiddenTab_should_onlyMarkQueriesStale', async () => {
      // Arrange
      await seed(['a'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      setHidden(true);

      // Act
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(calls()[0].refetchType).toBe('none');
      expect(queryClient.getQueryState(['a'])?.isInvalidated).toBe(true);
    });
  });

  describe('bulk and reconnection', () => {
    it('given_aType_should_invalidateQueriesListingOrShowingThatType', async () => {
      // Arrange
      await seed(['list'], [], { collections: ['scenarios'] });
      await seed(['detail'], [{ id: 's1' }], { refs: itemRefs('scenarios') });
      await seed(['other'], [{ id: 't1' }], {
        refs: itemRefs('tags'),
        collections: ['tags'],
      });

      // Act
      router.invalidateType('scenarios');

      // Assert
      expect(invalidatedKeys().sort()).toEqual(['detail', 'list']);
    });

    it('given_severalTypes_should_invalidateQueriesOfAnyOfThem', async () => {
      // Arrange
      await seed(['a'], [], { collections: ['injects'] });
      await seed(['b'], [], { collections: ['injectexpectations'] });
      await seed(['c'], [], { collections: ['tags'] });

      // Act
      router.invalidateType(['injects', 'injectexpectations']);

      // Assert
      expect(invalidatedKeys().sort()).toEqual(['a', 'b']);
    });

    it('given_noType_should_invalidateEveryLiveQueryButNotPlainOnes', async () => {
      // Arrange
      await seed(['a'], [], { collections: ['x'] });
      await seed(['b'], [{ id: '1' }], { refs: itemRefs('y') });
      await seed(['plain'], []);

      // Act
      router.invalidateType();

      // Assert
      expect(invalidatedKeys().sort()).toEqual(['a', 'b']);
    });

    it('given_invalidateAllLive_should_invalidateEveryLiveQuery', async () => {
      // Arrange
      await seed(['a'], [], { collections: ['x'] });
      await seed(['plain'], []);

      // Act
      router.invalidateAllLive();

      // Assert
      expect(invalidatedKeys()).toEqual(['a']);
    });

    it('given_aHiddenTab_should_onlyMarkStaleOnInvalidateAllLive', async () => {
      // Arrange
      await seed(['a'], [], { collections: ['x'] });
      setHidden(true);

      // Act
      router.invalidateAllLive();

      // Assert
      expect(calls()[0].refetchType).toBe('none');
    });

    it('given_aRecentInvalidation_should_throttleInvalidateAllLive', async () => {
      // Arrange
      await seed(['a'], [], { collections: ['x'] });

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
    const deferred = () => {
      let resolve: (value: unknown) => void = () => {};
      const promise = new Promise((res) => {
        resolve = res;
      });
      return {
        promise,
        resolve,
      };
    };

    const live: LiveMeta = {
      refs: itemRefs('assets'),
      collections: ['assets'],
    };

    const refetchList = async () => {
      await seed(['list'], [{ id: 'a1' }], live);
      const response = deferred();
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
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);
      const whileFetching = invalidate.mock.calls.length;
      response.resolve([{ id: 'a1' }]);
      await refetch;
      await vi.advanceTimersByTimeAsync(0);

      // Assert
      expect(whileFetching).toBe(0);
      expect(invalidatedKeys()).toEqual(['list']);
    });

    it('given_aChangeOnAnEntityTheResponseDoesNotShow_should_notRefetch', async () => {
      // Arrange
      const { response, refetch } = await refetchList();

      // Act: a steady flow of unrelated changes must not keep refetching a slow query
      router.applyChanges([change('assets', 'elsewhere', 'update')]);
      await vi.advanceTimersByTimeAsync(250);
      response.resolve([{ id: 'a1' }]);
      await refetch;
      await vi.advanceTimersByTimeAsync(0);

      // Assert
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_aQueryRemovedWhileChangesWereParkedForItsFetch_should_leaveNothingBehind', async () => {
      // Arrange: a change is parked for the fetch in flight, then the query leaves the cache
      const { response, refetch } = await refetchList();
      refetch.catch(() => {});
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);
      const cache = queryClient.getQueryCache();

      // Act: a new query with the same key settles after the removal
      cache.remove(cache.find({ queryKey: ['list'] })!);
      response.resolve([{ id: 'a1' }]);
      await vi.advanceTimersByTimeAsync(0);
      await seed(['list'], [{ id: 'a1' }], live);
      await vi.advanceTimersByTimeAsync(5000);

      // Assert
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_aQueryWithoutDataYet_should_neverMatch', async () => {
      // Arrange
      queryClient.getQueryCache().build(queryClient, {
        queryKey: ['pending'],
        meta: { live },
      });

      // Act
      router.applyChanges([change('assets', 'a1', 'create')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidate).not.toHaveBeenCalled();
    });

    it('given_anyInvalidation_should_neverCancelAnInFlightFetch', async () => {
      // Arrange
      await seed(['list'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);

      // Assert
      expect(invalidate.mock.calls[0][1]).toEqual({ cancelRefetch: false });
    });
  });

  describe('session events', () => {
    it('given_aLostStream_should_markLiveQueriesStaleWithoutFetching', async () => {
      // Arrange
      await seed(['live'], [], { collections: ['x'] });
      await seed(['plain'], []);

      // Act
      router.markAllLiveStale();

      // Assert
      expect(calls()).toEqual([{
        refetchType: 'none',
        keys: ['live'],
      }]);
    });
  });

  describe('invalidateAll', () => {
    it.each([
      [false, 'active'],
      [true, 'none'],
    ])('given_hidden_%s_should_invalidateLiveAndPlainQueriesWithRefetch_%s', async (hidden, refetchType) => {
      // Arrange
      await seed(['live'], [], { collections: ['x'] });
      await seed(['plain'], []);
      setHidden(hidden);

      // Act
      await router.invalidateAll();

      // Assert
      expect(invalidate).toHaveBeenCalledWith({ refetchType });
      expect(queryClient.getQueryState(['live'])?.isInvalidated).toBe(true);
      expect(queryClient.getQueryState(['plain'])?.isInvalidated).toBe(true);
    });
  });

  describe('immediate changes', () => {
    it('given_anImmediateChange_should_invalidateWithoutWaitingForTheWindow', async () => {
      // Arrange
      await seed(['list'], [{ id: 'a1' }], { refs: itemRefs('assets') });

      // Act
      const refreshed = router.applyChanges([change('assets', 'a1', 'delete')], { immediate: true });

      // Assert: the caller can wait for the refetch (a mutation stays pending until then)
      expect(invalidatedKeys()).toEqual(['list']);
      await expect(refreshed).resolves.toBeUndefined();
    });

    it('given_anImmediateChange_should_throttleTheStreamEchoThatFollows', async () => {
      // Arrange
      await seed(['list'], [{ id: 'a1' }], { refs: itemRefs('assets') });
      router.applyChanges([change('assets', 'a1', 'update')], { immediate: true });

      // Act
      router.applyChanges([change('assets', 'a1', 'update')]);
      await vi.advanceTimersByTimeAsync(250);
      const beforeTrailing = invalidate.mock.calls.length;
      await vi.advanceTimersByTimeAsync(1000);

      // Assert
      expect(beforeTrailing).toBe(1);
      expect(invalidate).toHaveBeenCalledTimes(2);
    });
  });
});
