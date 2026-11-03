import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, renderHook, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ENTITY_TYPES } from '../../../utils/query/entityChange';
import { type LiveChange, liveMeta, useApplyChanges, useLiveMutation } from '../../../utils/query/live';
import { type LiveRouter } from '../../../utils/query/liveRouter';
import { LiveRouterContext } from '../../../utils/query/liveRouterContext';

const createRouter = (): LiveRouter => ({
  applyChanges: vi.fn(() => Promise.resolve()),
  invalidateAllLive: vi.fn(),
  invalidateAll: vi.fn(() => Promise.resolve()),
  resetAll: vi.fn(() => Promise.resolve()),
  dispose: vi.fn(),
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

const wrapperFor = (router: LiveRouter) => {
  const client = new QueryClient();
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <LiveRouterContext.Provider value={router}>{children}</LiveRouterContext.Provider>
    </QueryClientProvider>
  );
  return Wrapper;
};

describe('useApplyChanges', () => {
  it('given_aProvider_should_returnTheRouterApplyChanges', () => {
    // Arrange
    const router = createRouter();

    // Act
    const { result } = renderHook(() => useApplyChanges(), { wrapper: wrapperFor(router) });

    // Assert
    expect(result.current).toBe(router.applyChanges);
  });

  it('given_noProvider_should_throw', () => {
    // Arrange
    vi.spyOn(console, 'error').mockImplementation(() => {});

    // Act
    const renderOutsideTheProvider = () => renderHook(() => useApplyChanges());

    // Assert
    expect(renderOutsideTheProvider).toThrow('AppQueryProvider');
  });
});

describe('useLiveMutation', () => {
  it('given_aSuccess_should_applyChangesBeforeTheCallerOnSuccess', async () => {
    // Arrange
    const order: string[] = [];
    const router = createRouter();
    vi.mocked(router.applyChanges).mockImplementation(() => {
      order.push('applyChanges');
      return Promise.resolve();
    });
    const onSuccess = vi.fn(() => {
      order.push('onSuccess');
    });
    const changes = vi.fn((data: { id: string }, variables: string): LiveChange[] => [{
      type: ENTITY_TYPES.ASSETS,
      id: data.id,
      action: 'create',
    }, {
      type: ENTITY_TYPES.TAGS,
      id: variables,
      action: 'update',
    }]);

    // Act
    const { result } = renderHook(
      () => useLiveMutation({
        mutationFn: (name: string) => Promise.resolve({ id: `id-${name}` }),
        changes,
        onSuccess,
      }),
      { wrapper: wrapperFor(router) },
    );
    result.current.mutate('x');

    // Assert
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(order).toEqual(['applyChanges', 'onSuccess']);
    expect(changes).toHaveBeenCalledWith({ id: 'id-x' }, 'x');
    expect(router.applyChanges).toHaveBeenCalledWith([
      {
        type: 'assets',
        id: 'id-x',
        action: 'create',
      },
      {
        type: 'tags',
        id: 'x',
        action: 'update',
      },
    ], { immediate: true });
    expect(onSuccess).toHaveBeenCalledWith({ id: 'id-x' }, 'x', undefined, expect.anything());
  });

  it('given_aFailure_should_notApplyChanges', async () => {
    // Arrange
    const router = createRouter();

    // Act
    const { result } = renderHook(
      () => useLiveMutation({
        mutationFn: () => Promise.reject(new Error('boom')),
        changes: () => [],
      }),
      { wrapper: wrapperFor(router) },
    );
    result.current.mutate();

    // Assert
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(router.applyChanges).not.toHaveBeenCalled();
  });

  it('given_noCallerOnSuccess_should_stillApplyChanges', async () => {
    // Arrange
    const router = createRouter();

    // Act
    const { result } = renderHook(
      () => useLiveMutation<string, unknown, void>({
        mutationFn: () => Promise.resolve('ok'),
        changes: () => [{
          type: ENTITY_TYPES.TAGS,
          id: '1',
          action: 'delete',
        }],
      }),
      { wrapper: wrapperFor(router) },
    );
    result.current.mutate();

    // Assert
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(router.applyChanges).toHaveBeenCalledTimes(1);
  });

  it('given_aRefetchInProgress_should_stayPendingUntilItEnds', async () => {
    // Arrange
    const router = createRouter();
    let endRefetch: () => void = () => {};
    vi.mocked(router.applyChanges).mockReturnValue(new Promise<void>((resolve) => {
      endRefetch = resolve;
    }));
    const onSuccess = vi.fn();
    const { result } = renderHook(
      () => useLiveMutation<string, unknown, void>({
        mutationFn: () => Promise.resolve('ok'),
        changes: () => [{
          type: ENTITY_TYPES.TAGS,
          id: '1',
          action: 'update',
        }],
        onSuccess,
      }),
      { wrapper: wrapperFor(router) },
    );

    // Act
    result.current.mutate();
    await waitFor(() => expect(router.applyChanges).toHaveBeenCalledTimes(1));
    const pendingDuringTheRefetch = result.current.isPending;
    const onSuccessDuringTheRefetch = onSuccess.mock.calls.length;
    endRefetch();

    // Assert
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(pendingDuringTheRefetch).toBe(true);
    expect(onSuccessDuringTheRefetch).toBe(0);
    expect(onSuccess).toHaveBeenCalledTimes(1);
  });

  it.each([
    ['changes throwing', () => {
      throw new Error('bad description');
    }, () => Promise.resolve()],
    ['applyChanges rejecting', () => [], () => Promise.reject(new Error('refetch failed'))],
  ])('given_%s_should_stillSucceedAndRunTheCallerOnSuccess', async (_label, changes, applyChanges) => {
    // Arrange
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const router = createRouter();
    vi.mocked(router.applyChanges).mockImplementation(applyChanges);
    const onSuccess = vi.fn();
    const { result } = renderHook(
      () => useLiveMutation<string, unknown, void>({
        mutationFn: () => Promise.resolve('ok'),
        changes,
        onSuccess,
      }),
      { wrapper: wrapperFor(router) },
    );

    // Act
    result.current.mutate();

    // Assert
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(onSuccess).toHaveBeenCalledWith('ok', undefined, undefined, expect.anything());
  });
});

describe('liveMeta', () => {
  it('given_typedRefs_should_passTheQueryDataToThem', () => {
    // Arrange
    const meta = liveMeta<{ asset_id: string }[]>({
      refs: assets => assets.map(asset => ({
        type: ENTITY_TYPES.ASSETS,
        id: asset.asset_id,
      })),
      collections: [ENTITY_TYPES.ASSETS],
      watch: [ENTITY_TYPES.AGENTS],
    });

    // Act
    const refs = meta.live.refs?.([{ asset_id: 'a1' }]);

    // Assert
    expect(refs).toEqual([{
      type: 'assets',
      id: 'a1',
    }]);
    expect(meta.live.collections).toEqual(['assets']);
    expect(meta.live.watch).toEqual(['agents']);
  });

  it('given_aTypeOutsideEntityTypes_should_notCompile', () => {
    // Act
    const meta = liveMeta<unknown>({
      // @ts-expect-error a type name must come from ENTITY_TYPES
      collections: ['asset'],
      // @ts-expect-error a type name must come from ENTITY_TYPES
      watch: ['agent'],
    });

    // Assert: checked by the type check, the call itself still works
    expect(meta.live.collections).toEqual(['asset']);
    expect(meta.live.watch).toEqual(['agent']);
  });
});
