import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { liveMeta, useApplyChanges, useLiveMutation } from '../../../utils/query/live';
import { type LiveRouter } from '../../../utils/query/liveRouter';
import { LiveRouterContext } from '../../../utils/query/liveRouterContext';
import { type EntityChange } from '../../../utils/stream/entityChange';
import { STREAM_TYPES } from '../../../utils/stream/streamTypes';

const createRouter = (): LiveRouter => ({
  applyChanges: vi.fn(() => Promise.resolve()),
  invalidateType: vi.fn(),
  invalidateAllLive: vi.fn(),
  markAllLiveStale: vi.fn(),
  invalidateAll: vi.fn(() => Promise.resolve()),
  dispose: vi.fn(),
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

    // Act / Assert
    expect(() => renderHook(() => useApplyChanges())).toThrow('AppQueryProvider');
    vi.restoreAllMocks();
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
    const changes = vi.fn((data: { id: string }, variables: string): EntityChange[] => [{
      type: 'assets',
      id: data.id,
      action: 'create',
    }, {
      type: 'tags',
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
      () => useLiveMutation({
        mutationFn: () => Promise.resolve('ok'),
        changes: () => [{
          type: 'tags',
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
});

describe('liveMeta', () => {
  it('given_typedRefs_should_passTheQueryDataToThem', () => {
    // Arrange
    const meta = liveMeta<{ executor_id: string }[]>({
      refs: executors => executors.map(executor => ({
        type: STREAM_TYPES.EXECUTORS,
        id: executor.executor_id,
      })),
      collections: [STREAM_TYPES.EXECUTORS],
    });

    // Act
    const refs = meta.live.refs?.([{ executor_id: 'e1' }]);

    // Assert
    expect(refs).toEqual([{
      type: 'executors',
      id: 'e1',
    }]);
    expect(meta.live.collections).toEqual(['executors']);
  });
});
